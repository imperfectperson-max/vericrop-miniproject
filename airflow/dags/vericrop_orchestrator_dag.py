"""
VeriCrop Orchestrator DAG - Coordinates multiple controllers through Kafka-based orchestration.
Triggers the orchestrator, monitors controller progress, and handles completion.
"""

from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.sensors.python import PythonSensor
import json
import logging
import os
import uuid

logger = logging.getLogger(__name__)

# Configuration from environment variables
KAFKA_BOOTSTRAP_SERVERS = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')
ORCHESTRATOR_TIMEOUT_MINUTES = int(os.getenv('ORCHESTRATOR_TIMEOUT_MINUTES', '10'))

default_args = {
    'owner': 'vericrop',
    'depends_on_past': False,
    'start_date': datetime(2024, 1, 1),
    'retries': 1,
    'retry_delay': timedelta(minutes=2),
    'execution_timeout': timedelta(minutes=ORCHESTRATOR_TIMEOUT_MINUTES + 5)
}

# Global state to track orchestration
orchestration_state = {}


def trigger_orchestrator(**context):
    """
    Trigger the orchestrator by sending a start event to Kafka.
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

        # Generate orchestration ID
        orchestration_id = f'ORCH_{context["ts_nodash"]}_{uuid.uuid4().hex[:8]}'
        
        # Define controllers to orchestrate
        controllers = [
            'scenarios',
            'delivery',
            'map',
            'temperature',
            'supplier_compliance',
            'simulations'
        ]
        
        # Create orchestrator start event
        start_event = {
            'orchestration_id': orchestration_id,
            'timestamp': int(datetime.now().timestamp() * 1000),
            'controllers': controllers,
            'parameters': {
                'origin': 'DAG',
                'run_id': context['run_id'],
                'execution_date': context['execution_date'].isoformat()
            }
        }

        logger.info(f"Preparing to trigger orchestration: {orchestration_id}")
        logger.info(f"Controllers to orchestrate: {', '.join(controllers)}")

        if kafka_available:
            # Configure Kafka producer
            producer = KafkaProducer(
                bootstrap_servers=[KAFKA_BOOTSTRAP_SERVERS],
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                acks='all',
                retries=3
            )

            # Send message to orchestrator start topic
            topic = 'vericrop.orchestrator.start'
            future = producer.send(
                topic,
                key=orchestration_id,
                value=start_event
            )

            # Wait for send to complete
            record_metadata = future.get(timeout=10)

            logger.info(f"✅ Sent orchestrator start event to Kafka topic '{topic}'")
            logger.info(f"   Orchestration ID: {orchestration_id}")
            logger.info(f"   Partition: {record_metadata.partition}, Offset: {record_metadata.offset}")

            producer.flush()
            producer.close()

            # Store orchestration state for sensor
            orchestration_state[orchestration_id] = {
                'status': 'triggered',
                'controllers': controllers,
                'start_time': datetime.now().isoformat()
            }

            # Push orchestration ID to XCom for downstream tasks
            context['task_instance'].xcom_push(key='orchestration_id', value=orchestration_id)
            context['task_instance'].xcom_push(key='kafka_sent', value=True)

        else:
            # Simulation mode - log what would be sent
            logger.info("📤 [SIMULATION] Would send to Kafka topic 'vericrop.orchestrator.start':")
            logger.info(f"   Orchestration ID: {orchestration_id}")
            logger.info(f"   Controllers: {', '.join(controllers)}")
            logger.info(f"   Payload: {json.dumps(start_event, indent=2)}")

            # Store simulation state
            orchestration_state[orchestration_id] = {
                'status': 'simulated',
                'controllers': controllers,
                'start_time': datetime.now().isoformat()
            }

            context['task_instance'].xcom_push(key='orchestration_id', value=orchestration_id)
            context['task_instance'].xcom_push(key='kafka_sent', value=False)

        return orchestration_id

    except Exception as e:
        logger.error(f"❌ Error triggering orchestrator: {str(e)}")
        raise


def check_orchestration_completion(**context):
    """
    Check if orchestration has completed by polling the completed topic.
    Returns True if completed, False otherwise (for sensor).
    """
    try:
        orchestration_id = context['task_instance'].xcom_pull(
            task_ids='trigger_orchestrator',
            key='orchestration_id'
        )
        
        if not orchestration_id:
            logger.error("No orchestration ID found in XCom")
            return False

        kafka_sent = context['task_instance'].xcom_pull(
            task_ids='trigger_orchestrator',
            key='kafka_sent'
        )

        # In simulation mode, auto-complete after checking
        if not kafka_sent:
            logger.info(f"[SIMULATION] Orchestration {orchestration_id} marked as completed")
            return True

        # Try to import kafka-python
        try:
            from kafka import KafkaConsumer
            kafka_available = True
        except ImportError:
            logger.warning("kafka-python not installed. Marking as completed.")
            return True

        if kafka_available:
            # Poll the orchestrator completed topic
            consumer = KafkaConsumer(
                'vericrop.orchestrator.completed',
                bootstrap_servers=[KAFKA_BOOTSTRAP_SERVERS],
                auto_offset_reset='earliest',
                enable_auto_commit=False,
                consumer_timeout_ms=5000,  # 5 seconds timeout
                value_deserializer=lambda m: json.loads(m.decode('utf-8'))
            )

            # Check for completion event
            for message in consumer:
                event = message.value
                if event.get('orchestration_id') == orchestration_id:
                    logger.info(f"✅ Orchestration completed: {orchestration_id}")
                    logger.info(f"   Success: {event.get('success')}")
                    logger.info(f"   Summary: {event.get('summary')}")
                    logger.info(f"   Results: {json.dumps(event.get('controller_results', {}), indent=2)}")
                    
                    consumer.close()
                    
                    # Store completion status in XCom
                    context['task_instance'].xcom_push(key='orchestration_success', value=event.get('success'))
                    context['task_instance'].xcom_push(key='orchestration_summary', value=event.get('summary'))
                    
                    return True

            consumer.close()
            logger.info(f"Orchestration {orchestration_id} not yet completed, continuing to wait...")
            return False

        else:
            # No Kafka available, mark as completed
            return True

    except Exception as e:
        logger.error(f"❌ Error checking orchestration completion: {str(e)}")
        # Don't fail the sensor, keep waiting
        return False


def handle_orchestration_result(**context):
    """
    Handle the orchestration result - success or failure.
    """
    orchestration_id = context['task_instance'].xcom_pull(
        task_ids='trigger_orchestrator',
        key='orchestration_id'
    )
    
    orchestration_success = context['task_instance'].xcom_pull(
        task_ids='wait_for_completion',
        key='orchestration_success'
    )
    
    orchestration_summary = context['task_instance'].xcom_pull(
        task_ids='wait_for_completion',
        key='orchestration_summary'
    )

    logger.info(f"=== Orchestration Result ===")
    logger.info(f"Orchestration ID: {orchestration_id}")
    logger.info(f"Success: {orchestration_success}")
    logger.info(f"Summary: {orchestration_summary}")
    logger.info(f"===========================")

    # Clean up state
    if orchestration_id in orchestration_state:
        del orchestration_state[orchestration_id]

    # Fail the DAG if orchestration failed
    if orchestration_success is False:
        raise Exception(f"Orchestration failed: {orchestration_summary}")
    
    logger.info("✅ Orchestration completed successfully")


# Define the DAG
with DAG(
    'vericrop_orchestrator',
    default_args=default_args,
    description='Orchestrate VeriCrop controllers through Kafka coordination',
    schedule_interval=None,  # Manual trigger only
    catchup=False,
    tags=['vericrop', 'orchestrator', 'kafka']
) as dag:

    # Task 1: Trigger orchestrator
    trigger_task = PythonOperator(
        task_id='trigger_orchestrator',
        python_callable=trigger_orchestrator,
        provide_context=True
    )

    # Task 2: Wait for orchestration to complete (sensor)
    wait_task = PythonSensor(
        task_id='wait_for_completion',
        python_callable=check_orchestration_completion,
        provide_context=True,
        poke_interval=10,  # Check every 10 seconds
        timeout=ORCHESTRATOR_TIMEOUT_MINUTES * 60,  # Total timeout
        mode='poke'
    )

    # Task 3: Handle result
    result_task = PythonOperator(
        task_id='handle_result',
        python_callable=handle_orchestration_result,
        provide_context=True
    )

    # Define task dependencies
    trigger_task >> wait_task >> result_task
