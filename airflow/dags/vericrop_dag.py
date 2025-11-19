"""
VeriCrop Airflow DAG - End-to-End Quality Evaluation Pipeline
Demonstrates Kafka message production and integration with VeriCrop services.
"""

from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.dummy import DummyOperator
import json
import logging

logger = logging.getLogger(__name__)

default_args = {
    'owner': 'vericrop',
    'depends_on_past': False,
    'start_date': datetime(2024, 1, 1),
    'retries': 1,
    'retry_delay': timedelta(minutes=2)
}

def produce_evaluation_request_to_kafka(**context):
    """
    Produce an evaluation request message to Kafka topic.
    This demonstrates end-to-end integration with the Java Kafka consumer.
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
        
        # Create evaluation request payload
        evaluation_request = {
            'batch_id': f'AIRFLOW_BATCH_{context["ts_nodash"]}',
            'image_path': '/data/samples/apple_fresh_001.jpg',
            'product_type': 'apple',
            'farmer_id': 'farmer_airflow_001',
            'timestamp': int(datetime.now().timestamp() * 1000)
        }
        
        logger.info(f"Preparing to send evaluation request: {evaluation_request['batch_id']}")
        
        if kafka_available:
            # Configure Kafka producer
            producer = KafkaProducer(
                bootstrap_servers=['localhost:9092'],
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                acks='all',
                retries=3
            )
            
            # Send message to Kafka topic
            topic = 'evaluation-requests'
            future = producer.send(
                topic,
                key=evaluation_request['batch_id'],
                value=evaluation_request
            )
            
            # Wait for send to complete
            record_metadata = future.get(timeout=10)
            
            logger.info(f"✅ Sent evaluation request to Kafka topic '{topic}'")
            logger.info(f"   Partition: {record_metadata.partition}, Offset: {record_metadata.offset}")
            
            producer.flush()
            producer.close()
            
            context['task_instance'].xcom_push(key='batch_id', value=evaluation_request['batch_id'])
            context['task_instance'].xcom_push(key='kafka_sent', value=True)
            
        else:
            # Simulation mode - log what would be sent
            logger.info("📤 [SIMULATION] Would send to Kafka topic 'evaluation-requests':")
            logger.info(f"   Batch ID: {evaluation_request['batch_id']}")
            logger.info(f"   Product: {evaluation_request['product_type']}")
            logger.info(f"   Farmer: {evaluation_request['farmer_id']}")
            logger.info(f"   Payload: {json.dumps(evaluation_request, indent=2)}")
            
            context['task_instance'].xcom_push(key='batch_id', value=evaluation_request['batch_id'])
            context['task_instance'].xcom_push(key='kafka_sent', value=False)
        
        return evaluation_request
        
    except Exception as e:
        logger.error(f"❌ Error producing evaluation request: {str(e)}")
        raise

def call_rest_api_evaluation(**context):
    """
    Alternative: Call REST API directly for evaluation.
    Demonstrates integration with VeriCrop REST API.
    """
    try:
        import requests
        
        batch_id = context['task_instance'].xcom_pull(task_ids='produce_kafka_message', key='batch_id')
        
        # Prepare API request
        api_url = 'http://localhost:8080/api/evaluate'
        payload = {
            'batch_id': batch_id,
            'product_type': 'apple',
            'farmer_id': 'farmer_airflow_001',
            'image_path': '/data/samples/apple_fresh_001.jpg'
        }
        
        logger.info(f"Calling REST API for batch: {batch_id}")
        
        try:
            response = requests.post(api_url, json=payload, timeout=30)
            
            if response.status_code == 200:
                result = response.json()
                logger.info(f"✅ Evaluation successful:")
                logger.info(f"   Quality Score: {result.get('quality_score')}")
                logger.info(f"   Pass/Fail: {result.get('pass_fail')}")
                logger.info(f"   Prediction: {result.get('prediction')}")
                logger.info(f"   Ledger ID: {result.get('ledger_id')}")
                
                context['task_instance'].xcom_push(key='evaluation_result', value=result)
                return result
            else:
                logger.error(f"❌ API returned status code: {response.status_code}")
                
        except requests.exceptions.ConnectionError:
            logger.warning("⚠️  API not available. Service may not be running.")
            logger.info("   Start with: ./gradlew :vericrop-gui:bootRun")
            logger.info("   Or: java -jar vericrop-gui/build/libs/vericrop-gui.jar")
        except requests.exceptions.Timeout:
            logger.error("❌ API request timed out")
        
    except Exception as e:
        logger.error(f"❌ Error calling REST API: {str(e)}")

def verify_ledger_record(**context):
    """
    Verify that the evaluation was recorded in the ledger.
    """
    try:
        import requests
        
        result = context['task_instance'].xcom_pull(task_ids='call_rest_api', key='evaluation_result')
        
        if result and 'ledger_id' in result:
            ledger_id = result['ledger_id']
            api_url = f'http://localhost:8080/api/shipments/{ledger_id}'
            
            logger.info(f"Verifying ledger record: {ledger_id}")
            
            try:
                response = requests.get(api_url, timeout=10)
                
                if response.status_code == 200:
                    shipment = response.json()
                    logger.info(f"✅ Ledger record verified:")
                    logger.info(f"   Shipment ID: {shipment.get('shipment_id')}")
                    logger.info(f"   Batch ID: {shipment.get('batch_id')}")
                    logger.info(f"   Integrity Check: {'PASS' if shipment.get('verified') else 'FAIL'}")
                    logger.info(f"   Ledger Hash: {shipment.get('ledger_hash', '')[:16]}...")
                    return shipment
                else:
                    logger.warning(f"⚠️  Ledger record not found: {response.status_code}")
                    
            except requests.exceptions.ConnectionError:
                logger.warning("⚠️  API not available for ledger verification")
                
        else:
            logger.warning("⚠️  No ledger ID available to verify")
            
    except Exception as e:
        logger.error(f"❌ Error verifying ledger: {str(e)}")

def generate_pipeline_summary(**context):
    """
    Generate summary of the VeriCrop pipeline execution.
    """
    try:
        batch_id = context['task_instance'].xcom_pull(task_ids='produce_kafka_message', key='batch_id')
        kafka_sent = context['task_instance'].xcom_pull(task_ids='produce_kafka_message', key='kafka_sent')
        result = context['task_instance'].xcom_pull(task_ids='call_rest_api', key='evaluation_result')
        
        summary = {
            'pipeline': 'VeriCrop Quality Evaluation',
            'execution_date': context['execution_date'].isoformat(),
            'batch_id': batch_id,
            'kafka_message_sent': kafka_sent,
            'evaluation_completed': result is not None,
            'quality_score': result.get('quality_score') if result else None,
            'ledger_recorded': result.get('ledger_id') is not None if result else False
        }
        
        logger.info("=" * 60)
        logger.info("📊 VeriCrop Pipeline Summary")
        logger.info("=" * 60)
        for key, value in summary.items():
            logger.info(f"   {key}: {value}")
        logger.info("=" * 60)
        
        return summary
        
    except Exception as e:
        logger.error(f"❌ Error generating summary: {str(e)}")

# Define the DAG
with DAG(
        'vericrop_evaluation_pipeline',
        default_args=default_args,
        description='End-to-end VeriCrop quality evaluation with Kafka integration',
        schedule_interval=timedelta(hours=1),  # Run every hour
        catchup=False,
        tags=['vericrop', 'quality', 'kafka', 'evaluation', 'integration']
) as dag:
    
    start = DummyOperator(task_id='start')
    
    # Step 1: Produce evaluation request to Kafka
    produce_kafka = PythonOperator(
        task_id='produce_kafka_message',
        python_callable=produce_evaluation_request_to_kafka,
        provide_context=True
    )
    
    # Step 2: Call REST API for evaluation
    call_api = PythonOperator(
        task_id='call_rest_api',
        python_callable=call_rest_api_evaluation,
        provide_context=True
    )
    
    # Step 3: Verify ledger record
    verify_ledger = PythonOperator(
        task_id='verify_ledger',
        python_callable=verify_ledger_record,
        provide_context=True
    )
    
    # Step 4: Generate summary
    generate_summary = PythonOperator(
        task_id='generate_summary',
        python_callable=generate_pipeline_summary,
        provide_context=True
    )
    
    end = DummyOperator(task_id='end')
    
    # Define task dependencies
    start >> produce_kafka >> call_api >> verify_ledger >> generate_summary >> end
