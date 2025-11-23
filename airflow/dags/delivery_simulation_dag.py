"""
VeriCrop Delivery Simulation DAG
Orchestrates batch delivery with quality decay simulation and Kafka notifications.
"""

from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.dummy import DummyOperator
import json
import logging
import os
import random

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

def start_delivery_simulation(**context):
    """
    Start delivery simulation for a batch.
    Initializes the delivery route and environmental conditions.
    """
    try:
        batch_id = context.get('dag_run').conf.get('batch_id', f'BATCH_{context["ts_nodash"]}')
        origin = context.get('dag_run').conf.get('origin', 'Farm Location')
        destination = context.get('dag_run').conf.get('destination', 'Distribution Center')
        
        simulation_data = {
            'simulation_id': f'SIM_{context["ts_nodash"]}',
            'batch_id': batch_id,
            'origin': origin,
            'destination': destination,
            'start_time': datetime.now().isoformat(),
            'initial_quality': context.get('dag_run').conf.get('initial_quality', 0.85),
            'initial_temperature': 6.0,  # Ideal cold chain temperature
            'initial_humidity': 75.0,
            'status': 'in_transit'
        }
        
        logger.info(f"🚚 Started delivery simulation: {simulation_data['simulation_id']}")
        logger.info(f"   Batch: {batch_id}, Route: {origin} → {destination}")
        
        # Push simulation data to XCom for downstream tasks
        context['task_instance'].xcom_push(key='simulation_data', value=simulation_data)
        
        return simulation_data
        
    except Exception as e:
        logger.error(f"❌ Error starting delivery simulation: {str(e)}")
        raise

def simulate_transit_conditions(**context):
    """
    Simulate environmental conditions during transit.
    Temperature and humidity fluctuations affect quality decay.
    """
    try:
        simulation_data = context['task_instance'].xcom_pull(
            task_ids='start_delivery_simulation', 
            key='simulation_data'
        )
        
        # Simulate temperature and humidity variations
        # In real system, these would come from IoT sensors
        waypoints = []
        current_quality = simulation_data['initial_quality']
        current_temp = simulation_data['initial_temperature']
        current_humidity = simulation_data['initial_humidity']
        
        # Simulate 5 waypoints along the route
        for i in range(5):
            # Simulate temperature variation (-2°C to +5°C from ideal)
            temp_variation = random.uniform(-2, 5)
            current_temp = 6.0 + temp_variation
            
            # Simulate humidity variation (±15% from ideal)
            humidity_variation = random.uniform(-15, 15)
            current_humidity = 75.0 + humidity_variation
            
            # Calculate quality decay based on conditions
            # Ideal: 4-8°C, 70-85% humidity
            if current_temp < 4 or current_temp > 8:
                temp_decay = abs(current_temp - 6.0) * 0.01
            else:
                temp_decay = 0
                
            if current_humidity < 70 or current_humidity > 85:
                humidity_decay = abs(current_humidity - 77.5) * 0.002
            else:
                humidity_decay = 0
            
            # Apply decay
            quality_decay = temp_decay + humidity_decay
            current_quality = max(0, current_quality - quality_decay)
            
            waypoint = {
                'waypoint_id': i + 1,
                'timestamp': datetime.now().isoformat(),
                'temperature': round(current_temp, 2),
                'humidity': round(current_humidity, 2),
                'quality': round(current_quality, 4),
                'quality_decay': round(quality_decay, 4),
                'location': f'Checkpoint {i + 1}'
            }
            waypoints.append(waypoint)
            
            logger.info(f"📍 Waypoint {i + 1}: temp={waypoint['temperature']}°C, "
                       f"humidity={waypoint['humidity']}%, quality={waypoint['quality']}")
        
        simulation_data['waypoints'] = waypoints
        simulation_data['final_quality'] = current_quality
        
        context['task_instance'].xcom_push(key='simulation_data', value=simulation_data)
        
        return simulation_data
        
    except Exception as e:
        logger.error(f"❌ Error simulating transit conditions: {str(e)}")
        raise

def publish_delivery_event_to_kafka(**context):
    """
    Publish delivery completion event to Kafka.
    Notifies relevant parties about delivery status and final quality.
    """
    try:
        # Try to import kafka-python
        try:
            from kafka import KafkaProducer
            kafka_available = True
        except ImportError:
            logger.warning("kafka-python not installed. Running in simulation mode.")
            kafka_available = False
        
        simulation_data = context['task_instance'].xcom_pull(
            task_ids='simulate_transit_conditions', 
            key='simulation_data'
        )
        
        delivery_event = {
            'event_type': 'delivery_completed',
            'simulation_id': simulation_data['simulation_id'],
            'batch_id': simulation_data['batch_id'],
            'origin': simulation_data['origin'],
            'destination': simulation_data['destination'],
            'start_time': simulation_data['start_time'],
            'end_time': datetime.now().isoformat(),
            'initial_quality': simulation_data['initial_quality'],
            'final_quality': simulation_data['final_quality'],
            'quality_degradation': simulation_data['initial_quality'] - simulation_data['final_quality'],
            'waypoint_count': len(simulation_data['waypoints']),
            'status': 'delivered'
        }
        
        if kafka_available:
            producer = KafkaProducer(
                bootstrap_servers=[KAFKA_BOOTSTRAP_SERVERS],
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                acks='all',
                retries=3
            )
            
            topic = 'logistics-events'
            future = producer.send(
                topic,
                key=delivery_event['batch_id'],
                value=delivery_event
            )
            
            record_metadata = future.get(timeout=10)
            logger.info(f"✅ Sent delivery event to Kafka topic '{topic}'")
            logger.info(f"   Batch: {delivery_event['batch_id']}, Quality: "
                       f"{delivery_event['initial_quality']:.2f} → {delivery_event['final_quality']:.2f}")
            
            producer.flush()
            producer.close()
        else:
            logger.info(f"📤 [SIMULATION] Would send delivery event to Kafka:")
            logger.info(f"   {json.dumps(delivery_event, indent=2)}")
        
        return delivery_event
        
    except Exception as e:
        logger.error(f"❌ Error publishing delivery event: {str(e)}")
        raise

def generate_delivery_report(**context):
    """
    Generate final delivery report with quality metrics.
    """
    try:
        simulation_data = context['task_instance'].xcom_pull(
            task_ids='simulate_transit_conditions', 
            key='simulation_data'
        )
        
        report = {
            'report_id': f"REPORT_{simulation_data['simulation_id']}",
            'batch_id': simulation_data['batch_id'],
            'delivery_summary': {
                'origin': simulation_data['origin'],
                'destination': simulation_data['destination'],
                'duration_hours': 2.5,  # Simulated duration
                'waypoints': len(simulation_data['waypoints'])
            },
            'quality_metrics': {
                'initial_quality': simulation_data['initial_quality'],
                'final_quality': simulation_data['final_quality'],
                'degradation_rate': simulation_data['initial_quality'] - simulation_data['final_quality'],
                'degradation_percent': ((simulation_data['initial_quality'] - simulation_data['final_quality']) / 
                                       simulation_data['initial_quality'] * 100)
            },
            'environmental_summary': {
                'avg_temperature': sum(wp['temperature'] for wp in simulation_data['waypoints']) / len(simulation_data['waypoints']),
                'avg_humidity': sum(wp['humidity'] for wp in simulation_data['waypoints']) / len(simulation_data['waypoints']),
                'temp_violations': sum(1 for wp in simulation_data['waypoints'] if wp['temperature'] < 4 or wp['temperature'] > 8),
                'humidity_violations': sum(1 for wp in simulation_data['waypoints'] if wp['humidity'] < 70 or wp['humidity'] > 85)
            },
            'generated_at': datetime.now().isoformat()
        }
        
        logger.info(f"📄 Generated delivery report: {report['report_id']}")
        logger.info(f"   Quality degradation: {report['quality_metrics']['degradation_percent']:.2f}%")
        logger.info(f"   Environmental violations: temp={report['environmental_summary']['temp_violations']}, "
                   f"humidity={report['environmental_summary']['humidity_violations']}")
        
        # In real system, this would be saved to logistics-and-supply-chain/reports/
        logger.info(f"💾 Report would be saved to: logistics-and-supply-chain/reports/delivery_report_{simulation_data['batch_id']}.json")
        
        return report
        
    except Exception as e:
        logger.error(f"❌ Error generating delivery report: {str(e)}")
        raise

# Create the DAG
dag = DAG(
    'vericrop_delivery_simulation',
    default_args=default_args,
    description='Simulate batch delivery with quality decay and Kafka notifications',
    schedule_interval=None,  # Trigger manually or via API
    catchup=False,
    tags=['vericrop', 'delivery', 'simulation', 'quality-decay']
)

# Define tasks
start_task = DummyOperator(
    task_id='start',
    dag=dag
)

start_delivery = PythonOperator(
    task_id='start_delivery_simulation',
    python_callable=start_delivery_simulation,
    provide_context=True,
    dag=dag
)

simulate_transit = PythonOperator(
    task_id='simulate_transit_conditions',
    python_callable=simulate_transit_conditions,
    provide_context=True,
    dag=dag
)

publish_event = PythonOperator(
    task_id='publish_delivery_event_to_kafka',
    python_callable=publish_delivery_event_to_kafka,
    provide_context=True,
    dag=dag
)

generate_report = PythonOperator(
    task_id='generate_delivery_report',
    python_callable=generate_delivery_report,
    provide_context=True,
    dag=dag
)

end_task = DummyOperator(
    task_id='end',
    dag=dag
)

# Define task dependencies
start_task >> start_delivery >> simulate_transit >> publish_event >> generate_report >> end_task
