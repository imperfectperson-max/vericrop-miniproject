"""
Airflow helpers for triggering DAGs with orchestration context.
Supports passing correlationId and other orchestration metadata to DAG runs.
"""

import json
import logging
from datetime import datetime
from typing import Dict, Any, Optional

from airflow.api.common.trigger_dag import trigger_dag
from airflow.models import DagRun

logger = logging.getLogger(__name__)


def trigger_dag_with_correlation_id(
    dag_id: str,
    correlation_id: str,
    batch_id: str,
    farmer_id: str,
    step_type: str,
    additional_conf: Optional[Dict[str, Any]] = None
) -> DagRun:
    """
    Trigger an Airflow DAG with orchestration context.
    
    Args:
        dag_id: The ID of the DAG to trigger
        correlation_id: The orchestration correlation ID for distributed tracing
        batch_id: The batch ID being processed
        farmer_id: The farmer/supplier ID
        step_type: The orchestration step type (scenarios, delivery, map, temperature, supplierCompliance, simulations)
        additional_conf: Optional additional configuration to pass to the DAG
        
    Returns:
        DagRun: The triggered DAG run
        
    Example:
        >>> trigger_dag_with_correlation_id(
        ...     dag_id='quality_monitoring',
        ...     correlation_id='ORCH-12345',
        ...     batch_id='BATCH-001',
        ...     farmer_id='FARMER-001',
        ...     step_type='temperature',
        ...     additional_conf={'threshold': 7.0}
        ... )
    """
    # Build configuration with orchestration context
    conf = {
        'correlation_id': correlation_id,
        'batch_id': batch_id,
        'farmer_id': farmer_id,
        'step_type': step_type,
        'triggered_at': datetime.utcnow().isoformat(),
    }
    
    # Merge additional configuration
    if additional_conf:
        conf.update(additional_conf)
    
    # Use correlation ID as run_id for traceability
    run_id = f"orchestration_{correlation_id}_{datetime.utcnow().strftime('%Y%m%d%H%M%S')}"
    
    logger.info(
        f"Triggering DAG '{dag_id}' with correlation_id={correlation_id}, "
        f"batch_id={batch_id}, step_type={step_type}"
    )
    
    try:
        dag_run = trigger_dag(
            dag_id=dag_id,
            run_id=run_id,
            conf=conf,
            execution_date=None,
            replace_microseconds=False
        )
        
        logger.info(
            f"Successfully triggered DAG '{dag_id}' with run_id={run_id}, "
            f"correlation_id={correlation_id}"
        )
        
        return dag_run
        
    except Exception as e:
        logger.error(
            f"Failed to trigger DAG '{dag_id}' with correlation_id={correlation_id}: {e}"
        )
        raise


def publish_orchestration_completion_event(
    correlation_id: str,
    batch_id: str,
    step_type: str,
    success: bool,
    message: str,
    data: Optional[Dict[str, Any]] = None
):
    """
    Publish a completion event for an orchestration step.
    This should be called at the end of a DAG to notify the orchestration service.
    
    Args:
        correlation_id: The orchestration correlation ID
        batch_id: The batch ID
        step_type: The step type that completed
        success: Whether the step completed successfully
        message: A message describing the result
        data: Optional result data
        
    Example:
        >>> # At the end of your DAG task
        >>> publish_orchestration_completion_event(
        ...     correlation_id=context['dag_run'].conf.get('correlation_id'),
        ...     batch_id=context['dag_run'].conf.get('batch_id'),
        ...     step_type=context['dag_run'].conf.get('step_type'),
        ...     success=True,
        ...     message='Temperature monitoring completed',
        ...     data={'avg_temp': 4.5, 'violations': 0}
        ... )
    """
    try:
        from kafka import KafkaProducer
        import os
        
        # Get Kafka configuration from environment
        kafka_bootstrap_servers = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')
        
        # Create Kafka producer
        producer = KafkaProducer(
            bootstrap_servers=kafka_bootstrap_servers.split(','),
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )
        
        # Build orchestration event
        event = {
            'correlation_id': correlation_id,
            'batch_id': batch_id,
            'farmer_id': 'AIRFLOW',  # Placeholder for airflow-triggered events
            'step_type': step_type,
            'event_type': 'COMPLETED' if success else 'FAILED',
            'timestamp': int(datetime.utcnow().timestamp() * 1000),
            'metadata': {
                'success': success,
                'message': message,
                'data': data or {},
                'source': 'airflow'
            }
        }
        
        # Determine topic based on step type
        topic = f"vericrop.orchestration.{step_type}"
        
        # Send event with correlation ID in header
        future = producer.send(
            topic,
            value=event,
            key=batch_id.encode('utf-8') if batch_id else None,
            headers=[('X-Correlation-Id', correlation_id.encode('utf-8'))]
        )
        
        # Wait for send to complete
        record_metadata = future.get(timeout=10)
        
        logger.info(
            f"Published orchestration completion event: correlation_id={correlation_id}, "
            f"step_type={step_type}, topic={topic}, partition={record_metadata.partition}"
        )
        
        producer.close()
        
    except ImportError:
        logger.warning(
            "kafka-python not installed. Cannot publish orchestration completion event. "
            "Install with: pip install kafka-python"
        )
    except Exception as e:
        logger.error(
            f"Failed to publish orchestration completion event for "
            f"correlation_id={correlation_id}, step_type={step_type}: {e}"
        )
        # Don't fail the DAG if event publishing fails
        pass


def get_orchestration_context(context: Dict[str, Any]) -> Dict[str, Any]:
    """
    Extract orchestration context from Airflow task context.
    
    Args:
        context: Airflow task context dictionary
        
    Returns:
        Dict containing orchestration metadata (correlation_id, batch_id, etc.)
        
    Example:
        >>> def my_task(**context):
        ...     orch_context = get_orchestration_context(context)
        ...     correlation_id = orch_context['correlation_id']
        ...     batch_id = orch_context['batch_id']
        ...     # ... use context in your task
    """
    dag_run = context.get('dag_run')
    if not dag_run or not dag_run.conf:
        return {}
    
    conf = dag_run.conf
    return {
        'correlation_id': conf.get('correlation_id'),
        'batch_id': conf.get('batch_id'),
        'farmer_id': conf.get('farmer_id'),
        'step_type': conf.get('step_type'),
        'additional_conf': {k: v for k, v in conf.items() 
                           if k not in ['correlation_id', 'batch_id', 'farmer_id', 'step_type']}
    }
