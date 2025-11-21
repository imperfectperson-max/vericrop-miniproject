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

def monitor_active_shipments(**context):
    """Monitor active shipments and their status"""
    try:
        # Simulate fetching active shipments
        # In production, this would query your logistics database or Kafka topics
        active_shipments = [
            {
                'batch_id': 'BATCH_A2386',
                'status': 'IN_TRANSIT',
                'current_location': 'Highway A - Mile 120',
                'temperature': 4.2,
                'humidity': 65,
                'eta': '2024-03-07 17:30:00',
                'vehicle_id': 'TRUCK_001'
            },
            {
                'batch_id': 'BATCH_A2387',
                'status': 'AT_WAREHOUSE',
                'current_location': 'Metro Fresh Warehouse',
                'temperature': 3.8,
                'humidity': 62,
                'eta': 'ARRIVED',
                'vehicle_id': None
            }
        ]

        context['task_instance'].xcom_push(key='active_shipments', value=active_shipments)

        # Check for delayed shipments
        delayed_shipments = [s for s in active_shipments if s['status'] == 'IN_TRANSIT']

        if delayed_shipments:
            return 'handle_delayed_shipments'
        else:
            return 'all_shipments_ok'

    except Exception as e:
        logger.error("❌ Error monitoring shipments: %s", e)
        return 'monitoring_failed'

def handle_delayed_shipments(**context):
    """Handle delayed or problematic shipments"""
    try:
        active_shipments = context['task_instance'].xcom_pull(task_ids='monitor_active_shipments', key='active_shipments')
        delayed_shipments = [s for s in active_shipments if s['status'] == 'IN_TRANSIT']

        for shipment in delayed_shipments:
            batch_id = shipment['batch_id']
            location = shipment['current_location']
            temperature = shipment['temperature']
            eta = shipment['eta']

            logger.info("🚚 Monitoring shipment: %s", batch_id)
            logger.info("   Location: %s, Temp: %s°C, ETA: %s", location, temperature, eta)

            # Check for issues
            if temperature > 7.0:
                logger.warning("🌡️ Temperature alert for %s: %s°C", batch_id, temperature)

            # Simulate sending status update
            # In production, this would update dashboards or send notifications

        logger.info("✅ Processed %s active shipments", len(delayed_shipments))

    except Exception as e:
        logger.error("❌ Error handling delayed shipments: %s", e)
        raise

def _clamp_temperature(value):
    try:
        t = float(value)
    except (TypeError, ValueError):
        return None
    # realistic clamp: -40°C .. 60°C
    return max(min(t, 60.0), -40.0)


def _clamp_humidity(value):
    try:
        h = float(value)
    except (TypeError, ValueError):
        return None
    # clamp 0..100%
    return max(min(h, 100.0), 0.0)


def check_environmental_compliance(**context):
    """Check environmental compliance for all shipments"""
    try:
        active_shipments = context['task_instance'].xcom_pull(task_ids='monitor_active_shipments', key='active_shipments') or []

        compliance_issues = []

        for shipment in active_shipments:
            batch_id = shipment.get('batch_id')
            temperature_raw = shipment.get('temperature')
            humidity_raw = shipment.get('humidity')

            temperature = _clamp_temperature(temperature_raw)
            humidity = _clamp_humidity(humidity_raw)

            # Skip shipments with invalid sensor data (log a notice)
            if temperature is None or humidity is None:
                logger.warning("Invalid sensor data for %s: temp=%s humidity=%s", batch_id, temperature_raw, humidity_raw)
                continue

            # Check compliance thresholds (business thresholds remain 2.0-7.0 °C and 50-80%)
            if temperature > 7.0 or temperature < 2.0:
                compliance_issues.append({
                    'batch_id': batch_id,
                    'issue': 'TEMPERATURE_OUT_OF_RANGE',
                    'value': temperature,
                    'threshold': '2.0-7.0°C'
                })

            if humidity > 80 or humidity < 50:
                compliance_issues.append({
                    'batch_id': batch_id,
                    'issue': 'HUMIDITY_OUT_OF_RANGE',
                    'value': humidity,
                    'threshold': '50-80%'
                })

        context['task_instance'].xcom_push(key='compliance_issues', value=compliance_issues)
        return 'handle_compliance_issues' if compliance_issues else 'all_compliant'

    except Exception as e:
        logger.error("❌ Error checking environmental compliance: %s", e)
        return 'compliance_check_failed'

def handle_compliance_issues(**context):
    """Handle environmental compliance issues"""
    try:
        compliance_issues = context['task_instance'].xcom_pull(task_ids='check_environmental_compliance', key='compliance_issues')

        for issue in compliance_issues:
            batch_id = issue['batch_id']
            issue_type = issue['issue']
            value = issue['value']
            threshold = issue['threshold']

            logger.error("🚨 COMPLIANCE ISSUE: %s - %s", batch_id, issue_type)
            logger.error("   Value: %s, Allowed: %s", value, threshold)

            # In production: trigger alerts, update product status, etc.

        logger.warning("⚠️ Handled %s compliance issues", len(compliance_issues))

    except Exception as e:
        logger.error("❌ Error handling compliance issues: %s", e)
        raise

def generate_logistics_report(**context):
    """Generate logistics performance report"""
    try:
        active_shipments = context['task_instance'].xcom_pull(task_ids='monitor_active_shipments', key='active_shipments') or []
        compliance_issues = context['task_instance'].xcom_pull(task_ids='check_environmental_compliance', key='compliance_issues') or []

        report = {
            'timestamp': datetime.now().isoformat(),
            'total_active_shipments': len(active_shipments),
            'in_transit': len([s for s in active_shipments if s['status'] == 'IN_TRANSIT']),
            'at_warehouse': len([s for s in active_shipments if s['status'] == 'AT_WAREHOUSE']),
            'compliance_issues': len(compliance_issues),
            'avg_temperature': sum(s['temperature'] for s in active_shipments) / len(active_shipments) if active_shipments else 0,
            'avg_humidity': sum(s['humidity'] for s in active_shipments) / len(active_shipments) if active_shipments else 0
        }

        logger.info("📦 Logistics Report: %s", report)
        context['task_instance'].xcom_push(key='logistics_report', value=report)

    except Exception as e:
        logger.error("❌ Error generating logistics report: %s", e)
        raise

with DAG(
        'logistics_tracking',
        default_args=default_args,
        description='Real-time logistics tracking and monitoring',
        schedule_interval=timedelta(minutes=15),  # Run every 15 minutes
        catchup=False,
        tags=['vericrop', 'logistics', 'tracking', 'monitoring']
) as dag:

    start = DummyOperator(task_id='start')

    monitor_shipments = BranchPythonOperator(
        task_id='monitor_active_shipments',
        python_callable=monitor_active_shipments,
        provide_context=True
    )

    handle_delayed = PythonOperator(
        task_id='handle_delayed_shipments',
        python_callable=handle_delayed_shipments,
        provide_context=True
    )

    all_ok = DummyOperator(task_id='all_shipments_ok')
    monitoring_failed = DummyOperator(task_id='monitoring_failed')

    check_compliance = BranchPythonOperator(
        task_id='check_environmental_compliance',
        python_callable=check_environmental_compliance,
        provide_context=True
    )

    handle_compliance = PythonOperator(
        task_id='handle_compliance_issues',
        python_callable=handle_compliance_issues,
        provide_context=True
    )

    all_compliant = DummyOperator(task_id='all_compliant')
    compliance_failed = DummyOperator(task_id='compliance_check_failed')

    generate_report = PythonOperator(
        task_id='generate_logistics_report',
        python_callable=generate_logistics_report,
        provide_context=True
    )

    end = DummyOperator(task_id='end')

    # Define workflow
    start >> monitor_shipments
    monitor_shipments >> [handle_delayed, all_ok, monitoring_failed]

    [handle_delayed, all_ok, monitoring_failed] >> check_compliance
    check_compliance >> [handle_compliance, all_compliant, compliance_failed]

    [handle_compliance, all_compliant, compliance_failed] >> generate_report >> end