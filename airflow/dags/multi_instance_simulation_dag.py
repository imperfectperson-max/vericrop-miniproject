"""
Multi-Instance Simulation DAG for VeriCrop

This DAG orchestrates end-to-end simulation scenarios with 3 instances of each 
controller type (Producer, Logistics, Consumer) running in parallel.

Scenarios:
- Scenario A: Normal Transit - All shipments complete successfully with high quality
- Scenario B: Temperature Breach - Some shipments experience temperature violations
- Scenario C: Route Disruption - Simulated delays affect delivery times

Each scenario runs for approximately 2 minutes and can be configured via Airflow Variables.
"""

from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.dummy import DummyOperator
from airflow.operators.bash import BashOperator
from airflow.models import Variable
import json
import logging
import os
import time
import random
import uuid

logger = logging.getLogger(__name__)

# Configuration from environment variables with defaults
KAFKA_BOOTSTRAP_SERVERS = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')
SIMULATION_DURATION_SECONDS = int(os.getenv('SIMULATION_DURATION_SECONDS', '120'))  # 2 minutes default
NUM_INSTANCES = int(os.getenv('NUM_CONTROLLER_INSTANCES', '3'))

# Kafka topic names
TOPIC_SIMULATION_CONTROL = 'simulation-control'
TOPIC_MAP_SIMULATION = 'map-simulation'
TOPIC_TEMPERATURE_COMPLIANCE = 'temperature-compliance'
TOPIC_QUALITY_ALERTS = 'quality-alerts'
TOPIC_BATCH_UPDATES = 'batch-updates'

default_args = {
    'owner': 'vericrop',
    'depends_on_past': False,
    'start_date': datetime(2024, 1, 1),
    'retries': 1,
    'retry_delay': timedelta(minutes=1),
    'execution_timeout': timedelta(minutes=5)  # Max 5 minutes per task
}


def get_kafka_producer():
    """Get Kafka producer or return None if not available."""
    try:
        from kafka import KafkaProducer
        return KafkaProducer(
            bootstrap_servers=[KAFKA_BOOTSTRAP_SERVERS],
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            key_serializer=lambda k: k.encode('utf-8') if k else None,
            acks='all',
            retries=3
        )
    except ImportError:
        logger.warning("kafka-python not installed. Running in simulation mode.")
        return None
    except Exception as e:
        logger.warning(f"Could not connect to Kafka: {e}. Running in simulation mode.")
        return None


def initialize_scenario(**context):
    """
    Initialize the simulation scenario with configuration.
    Sets up batch IDs and scenario parameters.
    """
    scenario_type = context['dag_run'].conf.get('scenario', 'normal')
    run_id = context['dag_run'].run_id
    
    logger.info(f"🚀 Initializing {scenario_type} scenario for run {run_id}")
    
    # Generate batch IDs for each producer instance
    batches = []
    for i in range(NUM_INSTANCES):
        batch_id = f"BATCH_{scenario_type.upper()}_{i+1}_{uuid.uuid4().hex[:8]}"
        farmer_id = f"FARMER_{i+1:03d}"
        
        # Scenario-specific configuration
        if scenario_type == 'normal':
            initial_quality = random.uniform(95.0, 99.0)
            temp_min = 2.0
            temp_max = 8.0
            expected_breaches = 0
        elif scenario_type == 'temperature_breach':
            initial_quality = random.uniform(90.0, 95.0)
            temp_min = -2.0 if i == 0 else 2.0  # First batch has temp breach
            temp_max = 12.0 if i == 0 else 8.0
            expected_breaches = 3 if i == 0 else 0
        elif scenario_type == 'route_disruption':
            initial_quality = random.uniform(92.0, 97.0)
            temp_min = 2.0
            temp_max = 8.0
            expected_breaches = 0
        else:
            initial_quality = 95.0
            temp_min = 2.0
            temp_max = 8.0
            expected_breaches = 0
        
        batch_config = {
            'batch_id': batch_id,
            'farmer_id': farmer_id,
            'instance_id': i + 1,
            'product_type': 'apple',
            'initial_quality': round(initial_quality, 2),
            'temp_min': temp_min,
            'temp_max': temp_max,
            'expected_breaches': expected_breaches,
            'created_at': datetime.now().isoformat()
        }
        batches.append(batch_config)
        logger.info(f"📦 Created batch config: {batch_id} from {farmer_id}")
    
    scenario_config = {
        'scenario_type': scenario_type,
        'run_id': run_id,
        'duration_seconds': SIMULATION_DURATION_SECONDS,
        'num_instances': NUM_INSTANCES,
        'batches': batches,
        'start_time': datetime.now().isoformat()
    }
    
    context['task_instance'].xcom_push(key='scenario_config', value=scenario_config)
    logger.info(f"✅ Scenario initialized with {NUM_INSTANCES} batches")
    
    return scenario_config


def start_producers(**context):
    """
    Start producer instances by publishing batch creation events.
    Each producer creates a batch with configured quality parameters.
    """
    config = context['task_instance'].xcom_pull(
        task_ids='initialize_scenario',
        key='scenario_config'
    )
    
    producer = get_kafka_producer()
    produced_batches = []
    
    for batch in config['batches']:
        batch_event = {
            'event_type': 'BATCH_CREATED',
            'batch_id': batch['batch_id'],
            'farmer_id': batch['farmer_id'],
            'product_type': batch['product_type'],
            'initial_quality': batch['initial_quality'],
            'temperature_tolerance': {
                'min': batch['temp_min'],
                'max': batch['temp_max']
            },
            'timestamp': datetime.now().isoformat(),
            'instance_id': batch['instance_id']
        }
        
        if producer:
            future = producer.send(
                TOPIC_BATCH_UPDATES,
                key=batch['batch_id'],
                value=batch_event
            )
            future.get(timeout=10)
            logger.info(f"✅ Published batch creation: {batch['batch_id']}")
        else:
            logger.info(f"📤 [SIMULATION] Would publish batch: {batch['batch_id']}")
        
        produced_batches.append(batch_event)
    
    if producer:
        producer.flush()
        producer.close()
    
    context['task_instance'].xcom_push(key='produced_batches', value=produced_batches)
    return produced_batches


def start_simulation_control(**context):
    """
    Broadcast simulation start events to all controller instances.
    This triggers the delivery simulation across all instances.
    """
    config = context['task_instance'].xcom_pull(
        task_ids='initialize_scenario',
        key='scenario_config'
    )
    
    producer = get_kafka_producer()
    control_events = []
    
    for batch in config['batches']:
        control_event = {
            'action': 'START',
            'simulationId': f"SIM_{batch['batch_id']}",
            'batchId': batch['batch_id'],
            'farmerId': batch['farmer_id'],
            'instanceId': f"airflow-{batch['instance_id']}",
            'timestamp': int(datetime.now().timestamp() * 1000),
            'scenario_type': config['scenario_type'],
            'duration_seconds': config['duration_seconds']
        }
        
        if producer:
            future = producer.send(
                TOPIC_SIMULATION_CONTROL,
                key=batch['batch_id'],
                value=control_event
            )
            future.get(timeout=10)
            logger.info(f"🎬 Started simulation for: {batch['batch_id']}")
        else:
            logger.info(f"📤 [SIMULATION] Would start simulation: {batch['batch_id']}")
        
        control_events.append(control_event)
        
        # Stagger starts by 2 seconds to avoid race conditions
        time.sleep(2)
    
    if producer:
        producer.flush()
        producer.close()
    
    context['task_instance'].xcom_push(key='control_events', value=control_events)
    return control_events


def simulate_logistics_updates(**context):
    """
    Simulate logistics updates including map positions and environmental data.
    Runs for the configured duration, publishing updates at regular intervals.
    """
    config = context['task_instance'].xcom_pull(
        task_ids='initialize_scenario',
        key='scenario_config'
    )
    
    producer = get_kafka_producer()
    duration = config['duration_seconds']
    scenario_type = config['scenario_type']
    
    # Calculate update interval (publish every 5 seconds)
    update_interval = 5
    total_updates = duration // update_interval
    
    logger.info(f"🚚 Starting logistics simulation for {duration} seconds ({total_updates} updates)")
    
    all_updates = []
    
    for update_num in range(total_updates):
        progress = ((update_num + 1) / total_updates) * 100
        
        for batch in config['batches']:
            # Calculate current position along route
            lat = 40.7128 + (progress / 100) * 0.5  # Simulated route
            lon = -74.0060 + (progress / 100) * 0.3
            
            # Determine temperature based on scenario
            if scenario_type == 'temperature_breach' and batch['instance_id'] == 1:
                # Inject temperature breach for first instance
                if 30 <= progress <= 50:
                    temperature = random.uniform(10.0, 15.0)  # Breach!
                else:
                    temperature = random.uniform(batch['temp_min'], batch['temp_max'])
            else:
                temperature = random.uniform(batch['temp_min'], batch['temp_max'])
            
            humidity = random.uniform(65.0, 85.0)
            
            # Determine status based on progress
            if progress < 10:
                status = 'DEPARTING'
                location = 'Farm Distribution Center'
            elif progress < 30:
                status = 'IN_TRANSIT'
                location = 'Highway 95'
            elif progress < 70:
                status = 'IN_TRANSIT'
                location = 'Interstate 78'
            elif progress < 90:
                status = 'APPROACHING'
                location = 'Local Distribution'
            else:
                status = 'ARRIVING'
                location = 'Consumer Warehouse'
            
            # Apply route disruption if applicable
            if scenario_type == 'route_disruption' and batch['instance_id'] == 2:
                if 40 <= progress <= 60:
                    status = 'DELAYED'
                    location = 'Traffic Delay - I-78'
            
            map_event = {
                'batch_id': batch['batch_id'],
                'timestamp': int(datetime.now().timestamp() * 1000),
                'latitude': round(lat, 6),
                'longitude': round(lon, 6),
                'location_name': location,
                'temperature': round(temperature, 2),
                'humidity': round(humidity, 2),
                'status': status,
                'progress_percent': round(progress, 1),
                'instance_id': batch['instance_id']
            }
            
            if producer:
                producer.send(
                    TOPIC_MAP_SIMULATION,
                    key=batch['batch_id'],
                    value=map_event
                )
            
            # Check for temperature compliance
            is_breach = temperature < batch['temp_min'] or temperature > batch['temp_max']
            if is_breach:
                alert_event = {
                    'batch_id': batch['batch_id'],
                    'alert_type': 'TEMPERATURE_BREACH',
                    'severity': 'HIGH' if abs(temperature - 5.0) > 5 else 'MEDIUM',
                    'temperature': round(temperature, 2),
                    'threshold_min': batch['temp_min'],
                    'threshold_max': batch['temp_max'],
                    'timestamp': int(datetime.now().timestamp() * 1000),
                    'message': f"Temperature {temperature:.1f}°C outside range [{batch['temp_min']}-{batch['temp_max']}°C]"
                }
                
                if producer:
                    producer.send(
                        TOPIC_QUALITY_ALERTS,
                        key=batch['batch_id'],
                        value=alert_event
                    )
                    logger.warning(f"🚨 Alert: {alert_event['message']} for {batch['batch_id']}")
            
            all_updates.append(map_event)
        
        if producer:
            producer.flush()
        
        # Log progress every 30 seconds
        if update_num % 6 == 0:
            logger.info(f"📍 Simulation progress: {progress:.1f}%")
        
        time.sleep(update_interval)
    
    if producer:
        producer.close()
    
    logger.info(f"✅ Logistics simulation completed with {len(all_updates)} updates")
    context['task_instance'].xcom_push(key='logistics_updates', value=len(all_updates))
    
    return all_updates


def stop_simulation_control(**context):
    """
    Broadcast simulation stop events to all controller instances.
    """
    config = context['task_instance'].xcom_pull(
        task_ids='initialize_scenario',
        key='scenario_config'
    )
    
    producer = get_kafka_producer()
    stop_events = []
    
    for batch in config['batches']:
        control_event = {
            'action': 'STOP',
            'simulationId': f"SIM_{batch['batch_id']}",
            'batchId': batch['batch_id'],
            'instanceId': f"airflow-{batch['instance_id']}",
            'timestamp': int(datetime.now().timestamp() * 1000),
            'completed': True
        }
        
        if producer:
            future = producer.send(
                TOPIC_SIMULATION_CONTROL,
                key=batch['batch_id'],
                value=control_event
            )
            future.get(timeout=10)
            logger.info(f"🛑 Stopped simulation for: {batch['batch_id']}")
        else:
            logger.info(f"📤 [SIMULATION] Would stop simulation: {batch['batch_id']}")
        
        stop_events.append(control_event)
    
    if producer:
        producer.flush()
        producer.close()
    
    context['task_instance'].xcom_push(key='stop_events', value=stop_events)
    return stop_events


def compute_final_quality(**context):
    """
    Compute final quality scores for all consumer instances.
    Aggregates temperature data and calculates quality degradation.
    """
    config = context['task_instance'].xcom_pull(
        task_ids='initialize_scenario',
        key='scenario_config'
    )
    
    scenario_type = config['scenario_type']
    final_results = []
    
    for batch in config['batches']:
        initial_quality = batch['initial_quality']
        
        # Calculate quality degradation based on scenario
        if scenario_type == 'normal':
            # Normal: minimal quality loss (1-3%)
            degradation = random.uniform(1.0, 3.0)
        elif scenario_type == 'temperature_breach':
            if batch['instance_id'] == 1:
                # Breached batch: significant quality loss (10-20%)
                degradation = random.uniform(10.0, 20.0)
            else:
                # Other batches: normal degradation
                degradation = random.uniform(1.0, 3.0)
        elif scenario_type == 'route_disruption':
            if batch['instance_id'] == 2:
                # Delayed batch: moderate quality loss (5-10%)
                degradation = random.uniform(5.0, 10.0)
            else:
                degradation = random.uniform(1.0, 3.0)
        else:
            degradation = random.uniform(1.0, 5.0)
        
        final_quality = max(0, initial_quality - degradation)
        avg_temperature = random.uniform(4.0, 7.0)  # Simulated average
        
        result = {
            'batch_id': batch['batch_id'],
            'farmer_id': batch['farmer_id'],
            'instance_id': batch['instance_id'],
            'initial_quality': round(initial_quality, 2),
            'final_quality': round(final_quality, 2),
            'quality_degradation': round(degradation, 2),
            'avg_temperature': round(avg_temperature, 2),
            'grade': 'PRIME' if final_quality >= 90 else 'STANDARD' if final_quality >= 70 else 'REJECT',
            'completed_at': datetime.now().isoformat()
        }
        
        final_results.append(result)
        logger.info(f"📊 Final quality for {batch['batch_id']}: {final_quality:.1f}% ({result['grade']})")
    
    # Compute aggregate statistics
    total_quality = sum(r['final_quality'] for r in final_results)
    avg_quality = total_quality / len(final_results)
    prime_count = sum(1 for r in final_results if r['grade'] == 'PRIME')
    
    aggregated = {
        'scenario_type': scenario_type,
        'num_batches': len(final_results),
        'avg_final_quality': round(avg_quality, 2),
        'prime_rate': round((prime_count / len(final_results)) * 100, 1),
        'batches': final_results,
        'summary_time': datetime.now().isoformat()
    }
    
    logger.info("=" * 60)
    logger.info(f"📈 SCENARIO SUMMARY: {scenario_type}")
    logger.info(f"   Batches processed: {aggregated['num_batches']}")
    logger.info(f"   Average quality: {aggregated['avg_final_quality']}%")
    logger.info(f"   Prime rate: {aggregated['prime_rate']}%")
    logger.info("=" * 60)
    
    context['task_instance'].xcom_push(key='final_results', value=aggregated)
    return aggregated


def generate_scenario_report(**context):
    """
    Generate final report for the simulation scenario.
    """
    config = context['task_instance'].xcom_pull(
        task_ids='initialize_scenario',
        key='scenario_config'
    )
    final_results = context['task_instance'].xcom_pull(
        task_ids='compute_final_quality',
        key='final_results'
    )
    logistics_count = context['task_instance'].xcom_pull(
        task_ids='simulate_logistics_updates',
        key='logistics_updates'
    )
    
    report = {
        'report_id': f"REPORT_{config['run_id']}",
        'scenario_type': config['scenario_type'],
        'execution': {
            'start_time': config['start_time'],
            'end_time': datetime.now().isoformat(),
            'duration_seconds': config['duration_seconds'],
            'num_instances': config['num_instances']
        },
        'results': {
            'avg_final_quality': final_results['avg_final_quality'],
            'prime_rate': final_results['prime_rate'],
            'total_logistics_updates': logistics_count or 0
        },
        'batches': final_results['batches']
    }
    
    logger.info("=" * 60)
    logger.info("📄 SIMULATION REPORT GENERATED")
    logger.info(f"   Report ID: {report['report_id']}")
    logger.info(f"   Scenario: {report['scenario_type']}")
    logger.info(f"   Duration: {report['execution']['duration_seconds']} seconds")
    logger.info(f"   Avg Quality: {report['results']['avg_final_quality']}%")
    logger.info("=" * 60)
    
    context['task_instance'].xcom_push(key='report', value=report)
    return report


# Create DAGs for each scenario
for scenario_name, scenario_desc in [
    ('normal', 'Normal Transit - All shipments complete with high quality'),
    ('temperature_breach', 'Temperature Breach - Some shipments exceed thresholds'),
    ('route_disruption', 'Route Disruption - Simulated delays affect deliveries')
]:
    dag = DAG(
        f'vericrop_multi_instance_{scenario_name}',
        default_args=default_args,
        description=f'Multi-instance simulation: {scenario_desc}',
        schedule_interval=None,  # Trigger manually
        catchup=False,
        tags=['vericrop', 'simulation', 'multi-instance', scenario_name],
        params={
            'scenario': scenario_name,
            'duration_seconds': SIMULATION_DURATION_SECONDS,
            'num_instances': NUM_INSTANCES
        }
    )
    
    with dag:
        start = DummyOperator(task_id='start')
        
        init_scenario = PythonOperator(
            task_id='initialize_scenario',
            python_callable=initialize_scenario,
            provide_context=True
        )
        
        start_prods = PythonOperator(
            task_id='start_producers',
            python_callable=start_producers,
            provide_context=True
        )
        
        start_sim = PythonOperator(
            task_id='start_simulation_control',
            python_callable=start_simulation_control,
            provide_context=True
        )
        
        logistics = PythonOperator(
            task_id='simulate_logistics_updates',
            python_callable=simulate_logistics_updates,
            provide_context=True,
            execution_timeout=timedelta(minutes=4)  # Allow extra time
        )
        
        stop_sim = PythonOperator(
            task_id='stop_simulation_control',
            python_callable=stop_simulation_control,
            provide_context=True
        )
        
        final_quality = PythonOperator(
            task_id='compute_final_quality',
            python_callable=compute_final_quality,
            provide_context=True
        )
        
        report = PythonOperator(
            task_id='generate_scenario_report',
            python_callable=generate_scenario_report,
            provide_context=True
        )
        
        end = DummyOperator(task_id='end')
        
        # Task dependencies
        start >> init_scenario >> start_prods >> start_sim >> logistics >> stop_sim >> final_quality >> report >> end
    
    # Register the DAG in globals
    globals()[f'vericrop_multi_instance_{scenario_name}'] = dag
