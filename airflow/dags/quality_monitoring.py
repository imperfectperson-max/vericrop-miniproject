from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.python import BranchPythonOperator
from airflow.operators.dummy import DummyOperator
import requests
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

def check_quality_thresholds(**context):
    """Check for quality threshold breaches"""
    try:
        # Simulate checking quality data from ML service
        # In production, this would query your ML service or database
        response = requests.get('http://ml-service:8000/quality/alerts', timeout=30)

        if response.status_code == 200:
            alerts = response.json().get('alerts', [])
        else:
            # Fallback: simulate some alerts for demo
            alerts = [
                {
                    'batch_id': 'BATCH_001',
                    'alert_type': 'QUALITY_DROP',
                    'severity': 'MEDIUM',
                    'current_value': 58.5,
                    'threshold': 60.0
                }
            ]

        context['task_instance'].xcom_push(key='quality_alerts', value=alerts)

        # Decide next step based on alerts
        if alerts:
            return 'process_quality_alerts'
        else:
            return 'no_alerts_found'

    except Exception as e:
        logger.error(f"❌ Error checking quality thresholds: {e}")
        return 'check_failed'

def process_quality_alerts(**context):
    """Process detected quality alerts"""
    try:
        alerts = context['task_instance'].xcom_pull(task_ids='check_quality_thresholds', key='quality_alerts')

        for alert in alerts:
            batch_id = alert.get('batch_id')
            alert_type = alert.get('alert_type')
            severity = alert.get('severity')
            current_value = alert.get('current_value')
            threshold = alert.get('threshold')

            logger.warning(f"🚨 Quality Alert: {batch_id} - {alert_type} (Severity: {severity})")
            logger.warning(f"   Current: {current_value}, Threshold: {threshold}")

            # Here you would:
            # 1. Send notifications
            # 2. Update dashboards
            # 3. Trigger corrective actions
            # 4. Log to database

        logger.info(f"✅ Processed {len(alerts)} quality alerts")

    except Exception as e:
        logger.error(f"❌ Error processing quality alerts: {e}")
        raise

def check_temperature_compliance(**context):
    """Check temperature compliance across shipments"""
    try:
        # Simulate temperature data check
        # In production, this would query IoT sensors or logistics database
        temperature_breaches = [
            {
                'batch_id': 'BATCH_TEMP_001',
                'location': 'Vehicle TRUCK_005',
                'current_temp': 8.5,
                'max_temp': 7.0,
                'duration_minutes': 45
            }
        ]

        context['task_instance'].xcom_push(key='temp_breaches', value=temperature_breaches)

        if temperature_breaches:
            return 'handle_temperature_breaches'
        else:
            return 'temperature_ok'

    except Exception as e:
        logger.error(f"❌ Error checking temperature compliance: {e}")
        return 'check_failed'

def handle_temperature_breaches(**context):
    """Handle temperature threshold breaches"""
    try:
        breaches = context['task_instance'].xcom_pull(task_ids='check_temperature_compliance', key='temp_breaches')

        for breach in breaches:
            batch_id = breach.get('batch_id')
            location = breach.get('location')
            current_temp = breach.get('current_temp')
            max_temp = breach.get('max_temp')

            logger.error(f"🌡️ TEMPERATURE BREACH: {batch_id} at {location}")
            logger.error(f"   Current: {current_temp}°C, Max Allowed: {max_temp}°C")

            # Actions:
            # 1. Notify logistics team
            # 2. Update product quality status
            # 3. Trigger quality reassessment
            # 4. Log incident

        logger.info(f"✅ Handled {len(breaches)} temperature breaches")

    except Exception as e:
        logger.error(f"❌ Error handling temperature breaches: {e}")
        raise

def generate_quality_summary(**context):
    """Generate quality monitoring summary"""
    try:
        quality_alerts = context['task_instance'].xcom_pull(task_ids='check_quality_thresholds', key='quality_alerts') or []
        temp_breaches = context['task_instance'].xcom_pull(task_ids='check_temperature_compliance', key='temp_breaches') or []

        summary = {
            'timestamp': datetime.now().isoformat(),
            'quality_alerts_count': len(quality_alerts),
            'temperature_breaches_count': len(temp_breaches),
            'total_issues': len(quality_alerts) + len(temp_breaches),
            'status': 'CRITICAL' if (len(quality_alerts) + len(temp_breaches)) > 5 else 'OK'
        }

        logger.info(f"📋 Quality Summary: {summary}")
        context['task_instance'].xcom_push(key='quality_summary', value=summary)

    except Exception as e:
        logger.error(f"❌ Error generating quality summary: {e}")
        raise

with DAG(
        'quality_monitoring',
        default_args=default_args,
        description='Real-time quality monitoring and alerting',
        schedule_interval=timedelta(minutes=30),  # Run every 30 minutes
        catchup=False,
        tags=['vericrop', 'quality', 'monitoring', 'alerts']
) as dag:

    start = DummyOperator(task_id='start')

    check_quality = BranchPythonOperator(
        task_id='check_quality_thresholds',
        python_callable=check_quality_thresholds,
        provide_context=True
    )

    process_alerts = PythonOperator(
        task_id='process_quality_alerts',
        python_callable=process_quality_alerts,
        provide_context=True
    )

    no_alerts = DummyOperator(task_id='no_alerts_found')
    check_failed_quality = DummyOperator(task_id='check_failed')

    check_temperature = BranchPythonOperator(
        task_id='check_temperature_compliance',
        python_callable=check_temperature_compliance,
        provide_context=True
    )

    handle_temp_breaches = PythonOperator(
        task_id='handle_temperature_breaches',
        python_callable=handle_temperature_breaches,
        provide_context=True
    )

    temp_ok = DummyOperator(task_id='temperature_ok')
    check_failed_temp = DummyOperator(task_id='check_failed_temp')

    generate_summary = PythonOperator(
        task_id='generate_quality_summary',
        python_callable=generate_quality_summary,
        provide_context=True
    )

    end = DummyOperator(task_id='end')

    # Define workflow
    start >> [check_quality, check_temperature]

    check_quality >> [process_alerts, no_alerts, check_failed_quality]
    check_temperature >> [handle_temp_breaches, temp_ok, check_failed_temp]

    [process_alerts, no_alerts, check_failed_quality, handle_temp_breaches, temp_ok, check_failed_temp] >> generate_summary >> end