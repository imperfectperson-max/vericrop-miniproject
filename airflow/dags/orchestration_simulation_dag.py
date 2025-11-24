"""
Orchestration-aware DAG for running simulations.
Demonstrates how to integrate with the orchestration service.
"""

from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.dummy import DummyOperator
import logging

from orchestration_helpers import (
    get_orchestration_context,
    publish_orchestration_completion_event
)

logger = logging.getLogger(__name__)

default_args = {
    'owner': 'vericrop',
    'depends_on_past': False,
    'start_date': datetime(2024, 1, 1),
    'retries': 1,
    'retry_delay': timedelta(minutes=2)
}


def run_simulation_task(**context):
    """
    Run a simulation task using orchestration context.
    """
    # Extract orchestration context from DAG run configuration
    orch_context = get_orchestration_context(context)
    
    correlation_id = orch_context.get('correlation_id')
    batch_id = orch_context.get('batch_id')
    step_type = orch_context.get('step_type', 'simulations')
    
    logger.info(f"Running simulation for orchestration: {correlation_id}")
    logger.info(f"Batch ID: {batch_id}")
    logger.info(f"Step Type: {step_type}")
    
    # TODO: Add actual simulation logic here
    # For now, just simulate some work
    import time
    time.sleep(2)
    
    result_data = {
        'simulation_duration': 2.0,
        'status': 'completed',
        'metrics': {
            'scenarios_processed': 5,
            'success_rate': 0.95
        }
    }
    
    logger.info(f"Simulation completed: {result_data}")
    
    # Store result in XCom for the completion task
    context['task_instance'].xcom_push(key='simulation_result', value=result_data)
    context['task_instance'].xcom_push(key='correlation_id', value=correlation_id)
    context['task_instance'].xcom_push(key='batch_id', value=batch_id)
    context['task_instance'].xcom_push(key='step_type', value=step_type)


def publish_completion(**context):
    """
    Publish orchestration completion event to Kafka.
    """
    # Get data from previous task
    ti = context['task_instance']
    correlation_id = ti.xcom_pull(task_ids='run_simulation', key='correlation_id')
    batch_id = ti.xcom_pull(task_ids='run_simulation', key='batch_id')
    step_type = ti.xcom_pull(task_ids='run_simulation', key='step_type')
    result_data = ti.xcom_pull(task_ids='run_simulation', key='simulation_result')
    
    if not correlation_id:
        logger.warning("No correlation_id found. Skipping completion event.")
        return
    
    # Publish completion event to Kafka
    publish_orchestration_completion_event(
        correlation_id=correlation_id,
        batch_id=batch_id,
        step_type=step_type,
        success=True,
        message='Simulation completed successfully',
        data=result_data
    )
    
    logger.info(f"Published completion event for correlation_id={correlation_id}")


# Define the DAG
with DAG(
    'orchestration_simulation',
    default_args=default_args,
    description='Orchestration-aware simulation DAG',
    schedule_interval=None,  # Triggered externally
    catchup=False,
    tags=['vericrop', 'orchestration', 'simulation']
) as dag:
    
    start = DummyOperator(task_id='start')
    
    simulate = PythonOperator(
        task_id='run_simulation',
        python_callable=run_simulation_task,
        provide_context=True
    )
    
    complete = PythonOperator(
        task_id='publish_completion',
        python_callable=publish_completion,
        provide_context=True
    )
    
    end = DummyOperator(task_id='end')
    
    start >> simulate >> complete >> end
