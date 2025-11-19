from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.email import EmailOperator
import requests
import json
import logging

logger = logging.getLogger(__name__)

default_args = {
    'owner': 'vericrop',
    'depends_on_past': False,
    'start_date': datetime(2024, 1, 1),
    'retries': 1,
    'retry_delay': timedelta(minutes=1)
}

def collect_alerts(**context):
    """Collect alerts from various sources"""
    try:
        # Simulate collecting alerts from different systems
        # In production, this would query Kafka, databases, APIs, etc.

        alerts = [
            {
                'id': 'ALERT_001',
                'type': 'QUALITY_DROP',
                'severity': 'HIGH',
                'batch_id': 'BATCH_A2386',
                'message': 'Quality score dropped below threshold',
                'timestamp': datetime.now().isoformat(),
                'source': 'ML_QUALITY_SERVICE'
            },
            {
                'id': 'ALERT_002',
                'type': 'TEMPERATURE_BREACH',
                'severity': 'MEDIUM',
                'batch_id': 'BATCH_A2387',
                'message': 'Temperature exceeded 7°C for 30 minutes',
                'timestamp': datetime.now().isoformat(),
                'source': 'IOT_SENSORS'
            },
            {
                'id': 'ALERT_003',
                'type': 'DELIVERY_DELAY',
                'severity': 'LOW',
                'batch_id': 'BATCH_A2388',
                'message': 'Shipment delayed by 45 minutes',
                'timestamp': datetime.now().isoformat(),
                'source': 'LOGISTICS_TRACKING'
            }
        ]

        context['task_instance'].xcom_push(key='collected_alerts', value=alerts)
        logger.info(f"✅ Collected {len(alerts)} alerts")

        return alerts

    except Exception as e:
        logger.error(f"❌ Error collecting alerts: {e}")
        context['task_instance'].xcom_push(key='collected_alerts', value=[])
        return []

def prioritize_alerts(**context):
    """Prioritize alerts based on severity and impact"""
    try:
        alerts = context['task_instance'].xcom_pull(task_ids='collect_alerts', key='collected_alerts') or []

        # Define priority rules
        severity_weights = {
            'CRITICAL': 100,
            'HIGH': 75,
            'MEDIUM': 50,
            'LOW': 25
        }

        # Calculate priority score for each alert
        prioritized_alerts = []
        for alert in alerts:
            severity = alert.get('severity', 'LOW')
            base_score = severity_weights.get(severity, 25)

            # Adjust score based on alert type
            if alert['type'] == 'TEMPERATURE_BREACH':
                base_score += 20
            elif alert['type'] == 'QUALITY_DROP':
                base_score += 15

            alert['priority_score'] = base_score
            prioritized_alerts.append(alert)

        # Sort by priority score (descending)
        prioritized_alerts.sort(key=lambda x: x['priority_score'], reverse=True)

        context['task_instance'].xcom_push(key='prioritized_alerts', value=prioritized_alerts)
        logger.info(f"✅ Prioritized {len(prioritized_alerts)} alerts")

        return prioritized_alerts

    except Exception as e:
        logger.error(f"❌ Error prioritizing alerts: {e}")
        raise

def route_alerts(**context):
    """Route alerts to appropriate teams based on type and severity"""
    try:
        alerts = context['task_instance'].xcom_pull(task_ids='prioritize_alerts', key='prioritized_alerts') or []

        routing_rules = {
            'QUALITY_DROP': 'quality_team',
            'TEMPERATURE_BREACH': 'logistics_team',
            'HUMIDITY_BREACH': 'logistics_team',
            'DELIVERY_DELAY': 'operations_team',
            'BLOCKCHAIN_ISSUE': 'tech_team'
        }

        routed_alerts = {}

        for alert in alerts:
            alert_type = alert['type']
            team = routing_rules.get(alert_type, 'operations_team')

            if team not in routed_alerts:
                routed_alerts[team] = []

            routed_alerts[team].append(alert)

            logger.info(f"📨 Routed {alert['id']} to {team} team")

        context['task_instance'].xcom_push(key='routed_alerts', value=routed_alerts)
        logger.info(f"✅ Routed alerts to {len(routed_alerts)} teams")

        return routed_alerts

    except Exception as e:
        logger.error(f"❌ Error routing alerts: {e}")
        raise

def send_critical_alerts(**context):
    """Send critical alerts via email"""
    try:
        alerts = context['task_instance'].xcom_pull(task_ids='prioritize_alerts', key='prioritized_alerts') or []

        # Filter critical/high alerts
        critical_alerts = [alert for alert in alerts if alert['severity'] in ['CRITICAL', 'HIGH']]

        if critical_alerts:
            alert_details = "\n".join([
                f"- {alert['type']} (Severity: {alert['severity']}): {alert['message']} - Batch: {alert['batch_id']}"
                for alert in critical_alerts
            ])

            email_content = f"""
            <h2>🚨 Critical Alerts - VeriCrop Supply Chain</h2>
            <p>The following critical alerts require immediate attention:</p>
            <ul>
            {''.join([f"<li><strong>{alert['type']}</strong> (Severity: {alert['severity']}): {alert['message']} - Batch: {alert['batch_id']}</li>" for alert in critical_alerts])}
            </ul>
            <p>Please take appropriate action immediately.</p>
            <hr>
            <p><em>Automated alert from VeriCrop Monitoring System</em></p>
            """

            context['task_instance'].xcom_push(key='critical_alert_email', value=email_content)
            logger.warning(f"🚨 Prepared {len(critical_alerts)} critical alerts for notification")
        else:
            logger.info("✅ No critical alerts to send")

    except Exception as e:
        logger.error(f"❌ Error preparing critical alerts: {e}")
        raise

def update_alert_dashboard(**context):
    """Update alert dashboard with current status"""
    try:
        alerts = context['task_instance'].xcom_pull(task_ids='prioritize_alerts', key='prioritized_alerts') or []
        routed_alerts = context['task_instance'].xcom_pull(task_ids='route_alerts', key='routed_alerts') or {}

        dashboard_data = {
            'timestamp': datetime.now().isoformat(),
            'total_alerts': len(alerts),
            'critical_alerts': len([a for a in alerts if a['severity'] == 'CRITICAL']),
            'high_alerts': len([a for a in alerts if a['severity'] == 'HIGH']),
            'medium_alerts': len([a for a in alerts if a['severity'] == 'MEDIUM']),
            'low_alerts': len([a for a in alerts if a['severity'] == 'LOW']),
            'teams_notified': len(routed_alerts),
            'alert_sources': list(set(a['source'] for a in alerts))
        }

        logger.info(f"📊 Alert Dashboard Updated: {dashboard_data}")
        context['task_instance'].xcom_push(key='alert_dashboard', value=dashboard_data)

    except Exception as e:
        logger.error(f"❌ Error updating alert dashboard: {e}")
        raise

with DAG(
        'alert_management',
        default_args=default_args,
        description='Comprehensive alert management and routing system',
        schedule_interval=timedelta(minutes=10),  # Run every 10 minutes
        catchup=False,
        tags=['vericrop', 'alerts', 'monitoring', 'notifications']
) as dag:

    collect_alerts_task = PythonOperator(
        task_id='collect_alerts',
        python_callable=collect_alerts,
        provide_context=True
    )

    prioritize_alerts_task = PythonOperator(
        task_id='prioritize_alerts',
        python_callable=prioritize_alerts,
        provide_context=True
    )

    route_alerts_task = PythonOperator(
        task_id='route_alerts',
        python_callable=route_alerts,
        provide_context=True
    )

    send_critical_alerts_task = PythonOperator(
        task_id='send_critical_alerts',
        python_callable=send_critical_alerts,
        provide_context=True
    )

    send_email_alerts = EmailOperator(
        task_id='send_alert_email',
        to=['operations@vericrop.com', 'alerts@vericrop.com'],
        subject='VeriCrop Critical Alerts - {{ ds }} {{ execution_date.strftime("%H:%M") }}',
        html_content="{{ task_instance.xcom_pull(task_ids='send_critical_alerts', key='critical_alert_email') }}",
        dag=dag
    )

    update_dashboard_task = PythonOperator(
        task_id='update_alert_dashboard',
        python_callable=update_alert_dashboard,
        provide_context=True
    )

    # Define workflow
    collect_alerts_task >> prioritize_alerts_task >> [route_alerts_task, send_critical_alerts_task]
    send_critical_alerts_task >> send_email_alerts
    [route_alerts_task, send_email_alerts] >> update_dashboard_task