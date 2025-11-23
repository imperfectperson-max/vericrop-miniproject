"""
VeriCrop Orchestrator DAG - Coordinated Subsystem Execution
Demonstrates scheduling the orchestrator to trigger multiple subsystems via Kafka or HTTP.
"""

from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.dummy import DummyOperator
from airflow.operators.http import SimpleHttpOperator
import json
import logging
import os

logger = logging.getLogger(__name__)

# Configuration from environment variables
KAFKA_BOOTSTRAP_SERVERS = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')
VERICROP_API_URL = os.getenv('VERICROP_API_URL', 'http://localhost:8080')

default_args = {
    'owner': 'vericrop',
    'depends_on_past': False,
    'start_date': datetime(2024, 1, 1),
    'retries': 1,
    'retry_delay': timedelta(minutes=2)
}


def publish_orchestrator_kafka(**context):
    """
    Alternative 1: Trigger orchestration by publishing directly to Kafka topics.
    This approach bypasses the REST API and publishes start messages directly.
    """
    try:
        # Try to import kafka-python
        try:
            from kafka import KafkaProducer
            kafka_available = True
        except ImportError:
            logger.warning("kafka-python not installed. Running in simulation mode.")
            logger.warning("Install with: pip install kafka-python")
            kafka_available = False

        run_id = f'AIRFLOW_ORCHESTRATOR_{context["ts_nodash"]}'
        subsystems = ['scenarios', 'delivery', 'map', 'temperature', 'supplier-compliance', 'simulations']
        
        logger.info(f"Triggering orchestration via Kafka - runId: {run_id}")

        if kafka_available:
            # Configure Kafka producer
            producer = KafkaProducer(
                bootstrap_servers=[KAFKA_BOOTSTRAP_SERVERS],
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                acks='all',
                retries=3
            )

            # Publish start messages to each subsystem topic
            topics = {
                'scenarios': 'vericrop.scenarios.start',
                'delivery': 'vericrop.delivery.start',
                'map': 'vericrop.map.start',
                'temperature': 'vericrop.temperature.start',
                'supplier-compliance': 'vericrop.supplier_compliance.start',
                'simulations': 'vericrop.simulations.start'
            }

            for subsystem in subsystems:
                topic = topics.get(subsystem)
                if topic:
                    message = {
                        'subsystem': subsystem,
                        'runId': run_id,
                        'timestamp': datetime.now().isoformat(),
                        'action': 'start'
                    }
                    
                    future = producer.send(topic, key=run_id, value=message)
                    record_metadata = future.get(timeout=10)
                    
                    logger.info(f"✅ Published start message for '{subsystem}' to topic '{topic}'")
                    logger.info(f"   Partition: {record_metadata.partition}, Offset: {record_metadata.offset}")

            producer.flush()
            producer.close()
            
            context['task_instance'].xcom_push(key='run_id', value=run_id)
            context['task_instance'].xcom_push(key='kafka_sent', value=True)

        else:
            # Simulation mode - log what would be sent
            logger.info("📤 [SIMULATION] Would publish start messages to Kafka topics:")
            for subsystem in subsystems:
                logger.info(f"   - {subsystem}: vericrop.{subsystem}.start")
            logger.info(f"   Run ID: {run_id}")

            context['task_instance'].xcom_push(key='run_id', value=run_id)
            context['task_instance'].xcom_push(key='kafka_sent', value=False)

        return {'runId': run_id, 'subsystems': subsystems}

    except Exception as e:
        logger.error(f"❌ Error publishing to Kafka: {str(e)}")
        raise


def verify_orchestration_status(**context):
    """
    Verify that the orchestration was triggered successfully.
    This is a placeholder for actual verification logic.
    """
    try:
        run_id = context['task_instance'].xcom_pull(task_ids='trigger_via_kafka', key='run_id')
        kafka_sent = context['task_instance'].xcom_pull(task_ids='trigger_via_kafka', key='kafka_sent')
        
        logger.info("=" * 60)
        logger.info("📊 Orchestration Status Summary")
        logger.info("=" * 60)
        logger.info(f"   Run ID: {run_id}")
        logger.info(f"   Kafka Messages Sent: {kafka_sent}")
        logger.info(f"   Execution Time: {context['execution_date'].isoformat()}")
        logger.info("=" * 60)
        
        # TODO: Add logic to verify subsystem execution status
        # Example: Query subsystem APIs or check Kafka consumer logs
        
        return {
            'runId': run_id,
            'status': 'triggered' if kafka_sent else 'simulated',
            'timestamp': datetime.now().isoformat()
        }

    except Exception as e:
        logger.error(f"❌ Error verifying orchestration: {str(e)}")


# Define the DAG
with DAG(
        'vericrop_orchestrator',
        default_args=default_args,
        description='Orchestrate coordinated runs of VeriCrop subsystems via Kafka or HTTP',
        schedule_interval=None,  # Manual trigger only (paused by default)
        catchup=False,
        is_paused_upon_creation=True,  # Start as paused
        tags=['vericrop', 'orchestrator', 'kafka', 'coordination']
) as dag:

    start = DummyOperator(task_id='start')

    # Alternative 1: Trigger orchestration via direct Kafka publishing
    trigger_kafka = PythonOperator(
        task_id='trigger_via_kafka',
        python_callable=publish_orchestrator_kafka
    )

    # Alternative 2: Trigger orchestration via HTTP REST API
    # Uncomment this task if you want to use the HTTP approach instead
    # trigger_http = SimpleHttpOperator(
    #     task_id='trigger_via_http',
    #     http_conn_id='vericrop_api',  # Configure this connection in Airflow UI
    #     endpoint='/api/orchestrator/run',
    #     method='POST',
    #     headers={'Content-Type': 'application/json'},
    #     data=json.dumps({
    #         'subsystems': ['scenarios', 'delivery', 'map', 'temperature', 'supplier-compliance', 'simulations']
    #     }),
    #     response_check=lambda response: response.status_code == 202,
    #     log_response=True
    # )

    # Verify orchestration status
    verify_status = PythonOperator(
        task_id='verify_status',
        python_callable=verify_orchestration_status
    )

    end = DummyOperator(task_id='end')

    # Define task dependencies
    # Using Kafka approach by default
    start >> trigger_kafka >> verify_status >> end
    
    # If using HTTP approach, comment the line above and uncomment below:
    # start >> trigger_http >> verify_status >> end
