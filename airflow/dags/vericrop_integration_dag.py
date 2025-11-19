"""
VeriCrop Integration DAG - Java Services & Kafka Integration
Demonstrates end-to-end integration between Airflow, Kafka, and VeriCrop Java services.
"""

from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.dummy import DummyOperator
from airflow.sensors.http_sensor import HttpSensor
import json
import logging
import os

logger = logging.getLogger(__name__)

# Environment variables for configuration
KAFKA_BOOTSTRAP_SERVERS = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')
JAVA_SERVICE_URL = os.getenv('JAVA_SERVICE_URL', 'http://localhost:8080')

default_args = {
    'owner': 'vericrop',
    'depends_on_past': False,
    'start_date': datetime(2024, 1, 1),
    'retries': 2,
    'retry_delay': timedelta(minutes=1)
}

def check_java_service_health(**context):
    """
    Check if the VeriCrop Java service is healthy and available.
    """
    try:
        import requests
        
        health_url = f'{JAVA_SERVICE_URL}/api/health'
        logger.info(f"Checking Java service health at: {health_url}")
        
        response = requests.get(health_url, timeout=10)
        
        if response.status_code == 200:
            health_data = response.json()
            logger.info(f"✅ Java service is healthy: {health_data}")
            context['task_instance'].xcom_push(key='service_healthy', value=True)
            context['task_instance'].xcom_push(key='kafka_enabled', value=health_data.get('kafka_enabled', False))
            return health_data
        else:
            logger.error(f"❌ Java service health check failed with status: {response.status_code}")
            context['task_instance'].xcom_push(key='service_healthy', value=False)
            return None
            
    except requests.exceptions.ConnectionError:
        logger.error("❌ Cannot connect to Java service. Is it running?")
        logger.info(f"   Start with: ./gradlew :vericrop-gui:bootRun")
        logger.info(f"   Or: java -jar vericrop-gui/build/libs/vericrop-gui-*.jar")
        context['task_instance'].xcom_push(key='service_healthy', value=False)
        return None
    except Exception as e:
        logger.error(f"❌ Error checking service health: {str(e)}")
        context['task_instance'].xcom_push(key='service_healthy', value=False)
        return None

def publish_quality_request_to_kafka(**context):
    """
    Publish a fruit quality evaluation request to Kafka.
    This demonstrates Airflow triggering the Java service pipeline via Kafka.
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
        batch_id = f'AIRFLOW_BATCH_{context["ts_nodash"]}'
        evaluation_request = {
            'batch_id': batch_id,
            'product_type': 'apple',
            'farmer_id': 'farmer_airflow_integration',
            'image_path': '/data/samples/apple_fresh_001.jpg',
            'timestamp': int(datetime.now().timestamp() * 1000)
        }
        
        logger.info(f"Preparing evaluation request: {batch_id}")
        
        if kafka_available:
            # Configure Kafka producer
            producer = KafkaProducer(
                bootstrap_servers=[KAFKA_BOOTSTRAP_SERVERS],
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                acks='all',
                retries=3
            )
            
            # Send message to fruit-quality topic
            topic = 'vericrop.fruit-quality'
            future = producer.send(
                topic,
                key=batch_id,
                value=evaluation_request
            )
            
            # Wait for send to complete
            record_metadata = future.get(timeout=10)
            
            logger.info(f"✅ Sent quality request to Kafka topic '{topic}'")
            logger.info(f"   Partition: {record_metadata.partition}, Offset: {record_metadata.offset}")
            
            producer.flush()
            producer.close()
            
            context['task_instance'].xcom_push(key='batch_id', value=batch_id)
            context['task_instance'].xcom_push(key='kafka_sent', value=True)
            
        else:
            # Simulation mode
            logger.info("📤 [SIMULATION] Would send to Kafka:")
            logger.info(f"   Topic: vericrop.fruit-quality")
            logger.info(f"   Batch ID: {batch_id}")
            logger.info(f"   Payload: {json.dumps(evaluation_request, indent=2)}")
            
            context['task_instance'].xcom_push(key='batch_id', value=batch_id)
            context['task_instance'].xcom_push(key='kafka_sent', value=False)
        
        return evaluation_request
        
    except Exception as e:
        logger.error(f"❌ Error publishing to Kafka: {str(e)}")
        raise

def call_java_rest_api(**context):
    """
    Call the VeriCrop Java REST API to trigger quality evaluation.
    This demonstrates direct HTTP integration with Java services.
    """
    try:
        import requests
        
        batch_id = context['task_instance'].xcom_pull(task_ids='publish_kafka_message', key='batch_id')
        
        # Prepare API request
        api_url = f'{JAVA_SERVICE_URL}/api/evaluate'
        payload = {
            'batch_id': batch_id,
            'product_type': 'apple',
            'farmer_id': 'farmer_airflow_integration'
        }
        
        logger.info(f"Calling Java REST API: {api_url}")
        logger.info(f"Payload: {json.dumps(payload, indent=2)}")
        
        try:
            response = requests.post(api_url, json=payload, timeout=30)
            
            if response.status_code == 200:
                result = response.json()
                logger.info(f"✅ Evaluation successful:")
                logger.info(f"   Batch ID: {result.get('batch_id')}")
                logger.info(f"   Quality Score: {result.get('quality_score')}")
                logger.info(f"   Pass/Fail: {result.get('pass_fail')}")
                logger.info(f"   Prediction: {result.get('prediction')}")
                
                context['task_instance'].xcom_push(key='evaluation_result', value=result)
                return result
            else:
                logger.error(f"❌ API returned status code: {response.status_code}")
                logger.error(f"   Response: {response.text}")
                
        except requests.exceptions.ConnectionError:
            logger.warning("⚠️  Java API not available. Service may not be running.")
            logger.info(f"   Expected URL: {api_url}")
        except requests.exceptions.Timeout:
            logger.error("❌ API request timed out")
        
    except Exception as e:
        logger.error(f"❌ Error calling Java REST API: {str(e)}")

def publish_supplychain_event(**context):
    """
    Publish a supply chain event to Kafka.
    Simulates a warehouse storage event after quality evaluation.
    """
    try:
        # Try to import kafka-python
        try:
            from kafka import KafkaProducer
            kafka_available = True
        except ImportError:
            logger.warning("kafka-python not installed. Running in simulation mode.")
            kafka_available = False
        
        batch_id = context['task_instance'].xcom_pull(task_ids='publish_kafka_message', key='batch_id')
        result = context['task_instance'].xcom_pull(task_ids='call_rest_api', key='evaluation_result')
        
        # Create storage event
        storage_event = {
            'batch_id': batch_id,
            'location': 'Central Warehouse A',
            'temperature': 4.5,
            'humidity': 65,
            'timestamp': int(datetime.now().timestamp() * 1000),
            'quality_score': result.get('quality_score') if result else None
        }
        
        logger.info(f"Publishing supply chain event for batch: {batch_id}")
        
        if kafka_available:
            producer = KafkaProducer(
                bootstrap_servers=[KAFKA_BOOTSTRAP_SERVERS],
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                acks='all',
                retries=3
            )
            
            topic = 'vericrop.supplychain-events'
            event_type = 'STORAGE'
            
            future = producer.send(
                topic,
                key=event_type,
                value=storage_event
            )
            
            record_metadata = future.get(timeout=10)
            
            logger.info(f"✅ Sent STORAGE event to Kafka topic '{topic}'")
            logger.info(f"   Partition: {record_metadata.partition}, Offset: {record_metadata.offset}")
            
            producer.flush()
            producer.close()
            
        else:
            logger.info("📤 [SIMULATION] Would send supply chain event:")
            logger.info(f"   Topic: vericrop.supplychain-events")
            logger.info(f"   Event Type: STORAGE")
            logger.info(f"   Payload: {json.dumps(storage_event, indent=2)}")
        
        return storage_event
        
    except Exception as e:
        logger.error(f"❌ Error publishing supply chain event: {str(e)}")

def create_customer_record(**context):
    """
    Create a customer record via the CustomerController REST API.
    Demonstrates integration with the new customer management endpoints.
    """
    try:
        import requests
        
        # Create a new customer
        customer_data = {
            'name': 'Airflow Test Farmer',
            'email': f'farmer_{context["ts_nodash"]}@vericrop.test',
            'phone': '+1234567890',
            'customerType': 'FARMER',
            'address': '123 Farm Road, Agricultural District',
            'active': True
        }
        
        api_url = f'{JAVA_SERVICE_URL}/api/customers'
        logger.info(f"Creating customer via API: {api_url}")
        
        try:
            response = requests.post(api_url, json=customer_data, timeout=10)
            
            if response.status_code == 201:
                customer = response.json()
                logger.info(f"✅ Customer created successfully:")
                logger.info(f"   ID: {customer.get('id')}")
                logger.info(f"   Name: {customer.get('name')}")
                logger.info(f"   Email: {customer.get('email')}")
                
                context['task_instance'].xcom_push(key='customer_id', value=customer.get('id'))
                return customer
            else:
                logger.warning(f"⚠️  Failed to create customer: {response.status_code}")
                
        except requests.exceptions.ConnectionError:
            logger.warning("⚠️  Customer API not available")
        
    except Exception as e:
        logger.error(f"❌ Error creating customer: {str(e)}")

def generate_integration_summary(**context):
    """
    Generate summary of the integration pipeline execution.
    """
    try:
        batch_id = context['task_instance'].xcom_pull(task_ids='publish_kafka_message', key='batch_id')
        kafka_sent = context['task_instance'].xcom_pull(task_ids='publish_kafka_message', key='kafka_sent')
        result = context['task_instance'].xcom_pull(task_ids='call_rest_api', key='evaluation_result')
        customer_id = context['task_instance'].xcom_pull(task_ids='create_customer', key='customer_id')
        
        summary = {
            'pipeline': 'VeriCrop Java Services Integration',
            'execution_date': context['execution_date'].isoformat(),
            'batch_id': batch_id,
            'kafka_bootstrap_servers': KAFKA_BOOTSTRAP_SERVERS,
            'java_service_url': JAVA_SERVICE_URL,
            'kafka_message_sent': kafka_sent,
            'evaluation_completed': result is not None,
            'quality_score': result.get('quality_score') if result else None,
            'customer_created': customer_id is not None,
            'customer_id': customer_id
        }
        
        logger.info("=" * 70)
        logger.info("📊 VeriCrop Integration Pipeline Summary")
        logger.info("=" * 70)
        for key, value in summary.items():
            logger.info(f"   {key}: {value}")
        logger.info("=" * 70)
        
        return summary
        
    except Exception as e:
        logger.error(f"❌ Error generating summary: {str(e)}")

# Define the DAG
with DAG(
        'vericrop_java_integration',
        default_args=default_args,
        description='Complete integration pipeline for VeriCrop Java services, Kafka, and Airflow',
        schedule_interval=timedelta(hours=2),  # Run every 2 hours
        catchup=False,
        tags=['vericrop', 'java', 'kafka', 'integration', 'customer']
) as dag:
    
    start = DummyOperator(task_id='start')
    
    # Step 1: Check Java service health
    check_health = PythonOperator(
        task_id='check_service_health',
        python_callable=check_java_service_health,
        provide_context=True
    )
    
    # Step 2: Publish quality request to Kafka
    publish_kafka = PythonOperator(
        task_id='publish_kafka_message',
        python_callable=publish_quality_request_to_kafka,
        provide_context=True
    )
    
    # Step 3: Call Java REST API
    call_api = PythonOperator(
        task_id='call_rest_api',
        python_callable=call_java_rest_api,
        provide_context=True
    )
    
    # Step 4: Publish supply chain event
    publish_event = PythonOperator(
        task_id='publish_supplychain_event',
        python_callable=publish_supplychain_event,
        provide_context=True
    )
    
    # Step 5: Create customer record
    create_customer = PythonOperator(
        task_id='create_customer',
        python_callable=create_customer_record,
        provide_context=True
    )
    
    # Step 6: Generate summary
    generate_summary = PythonOperator(
        task_id='generate_summary',
        python_callable=generate_integration_summary,
        provide_context=True
    )
    
    end = DummyOperator(task_id='end')
    
    # Define task dependencies
    start >> check_health >> publish_kafka >> call_api >> [publish_event, create_customer] >> generate_summary >> end
