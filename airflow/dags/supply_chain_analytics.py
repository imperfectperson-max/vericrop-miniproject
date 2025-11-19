from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.email import EmailOperator
from airflow.operators.bash import BashOperator
import requests
import json
import pandas as pd
from sqlalchemy import create_engine
import logging

logger = logging.getLogger(__name__)

default_args = {
    'owner': 'vericrop',
    'depends_on_past': False,
    'start_date': datetime(2024, 1, 1),
    'email_on_failure': True,
    'email_on_retry': False,
    'retries': 2,
    'retry_delay': timedelta(minutes=5)
}

def extract_quality_data(**context):
    """Extract quality data from ML service and Kafka topics"""
    try:
        # Extract from ML service
        ml_response = requests.get('http://ml-service:8000/dashboard/farm', timeout=30)
        if ml_response.status_code == 200:
            ml_data = ml_response.json()
            logger.info(f"✅ Extracted {len(ml_data.get('recent_batches', []))} batches from ML service")
        else:
            logger.error(f"❌ ML service error: {ml_response.status_code}")
            ml_data = {}

        # Push data to XCom for downstream tasks
        context['task_instance'].xcom_push(key='ml_quality_data', value=ml_data)
        return ml_data

    except Exception as e:
        logger.error(f"❌ Error extracting quality data: {e}")
        raise

def calculate_supplier_kpis(**context):
    """Calculate supplier performance KPIs"""
    try:
        quality_data = context['task_instance'].xcom_pull(task_ids='extract_quality_data', key='ml_quality_data')

        if not quality_data:
            logger.warning("No quality data available")
            return {}

        kpis = quality_data.get('kpis', {})
        recent_batches = quality_data.get('recent_batches', [])

        # Calculate advanced KPIs
        total_batches = kpis.get('total_batches_today', 0)
        avg_quality = kpis.get('average_quality', 0)
        rejection_rate = kpis.get('rejection_rate', 0)
        prime_percentage = kpis.get('prime_percentage', 0)

        # Calculate trend (simple moving average)
        quality_scores = [float(batch.get('quality_score', 0)) for batch in recent_batches if batch.get('quality_score')]
        quality_trend = sum(quality_scores[-5:]) / len(quality_scores[-5:]) if len(quality_scores) >= 5 else avg_quality

        advanced_kpis = {
            'total_batches': total_batches,
            'avg_quality': avg_quality,
            'rejection_rate': rejection_rate,
            'prime_percentage': prime_percentage,
            'quality_trend': quality_trend,
            'batch_count_trend': 'up' if total_batches > 10 else 'stable',  # Simplified
            'timestamp': datetime.now().isoformat()
        }

        logger.info(f"📊 Calculated KPIs: {advanced_kpis}")
        context['task_instance'].xcom_push(key='supplier_kpis', value=advanced_kpis)
        return advanced_kpis

    except Exception as e:
        logger.error(f"❌ Error calculating supplier KPIs: {e}")
        raise

def generate_daily_report(**context):
    """Generate comprehensive daily analytics report"""
    try:
        kpis = context['task_instance'].xcom_pull(task_ids='calculate_supplier_kpis', key='supplier_kpis')

        if not kpis:
            kpis = {
                'total_batches': 0,
                'avg_quality': 0,
                'rejection_rate': 0,
                'prime_percentage': 0,
                'quality_trend': 0
            }

        # Generate markdown report
        report_date = datetime.now().strftime('%Y-%m-%d')
        report = f"""
# 📈 VERICROP DAILY ANALYTICS REPORT
**Date**: {report_date}
**Generated**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

## 📊 KEY PERFORMANCE INDICATORS

| Metric | Value | Trend |
|--------|-------|-------|
| Total Batches Processed | {kpis['total_batches']} | {kpis.get('batch_count_trend', 'stable').upper()} |
| Average Quality Score | {kpis['avg_quality']}% | {'↗️' if kpis['quality_trend'] > kpis['avg_quality'] else '↘️'} |
| Prime Quality Percentage | {kpis['prime_percentage']}% | - |
| Product Rejection Rate | {kpis['rejection_rate']}% | - |

## 🎯 RECOMMENDATIONS

### 🚀 Opportunities
- Monitor suppliers with quality below 80% for improvement
- Optimize logistics routes for temperature-sensitive products
- Review rejection causes for process improvement

### ⚠️ Areas for Attention
{f"- Quality trend is {'improving' if kpis['quality_trend'] > kpis['avg_quality'] else 'declining'}"}
{f"- Rejection rate is {'high' if kpis['rejection_rate'] > 5 else 'acceptable'}"}

## 📈 NEXT STEPS
1. Schedule supplier review meetings for underperforming partners
2. Analyze temperature breach patterns in logistics
3. Review quality assessment criteria

---
*Report generated automatically by VeriCrop Analytics*
"""

        # Save report to file
        report_filename = f"/opt/airflow/logs/daily_report_{report_date}.md"
        with open(report_filename, 'w') as f:
            f.write(report)

        logger.info(f"✅ Daily report generated: {report_filename}")
        context['task_instance'].xcom_push(key='daily_report', value=report)
        return report

    except Exception as e:
        logger.error(f"❌ Error generating daily report: {e}")
        raise

def store_analytics_data(**context):
    """Store analytics data in database for historical tracking"""
    try:
        kpis = context['task_instance'].xcom_pull(task_ids='calculate_supplier_kpis', key='supplier_kpis')

        if kpis:
            # Create DataFrame
            df = pd.DataFrame([kpis])
            df['report_date'] = datetime.now().date()

            # Store in database (SQLite for simplicity, in production use PostgreSQL)
            engine = create_engine('sqlite:////opt/airflow/logs/vericrop_analytics.db')
            df.to_sql('supplier_kpis', engine, if_exists='append', index=False)

            logger.info(f"✅ Stored {len(df)} KPI records in database")
        else:
            logger.warning("No KPI data to store")

    except Exception as e:
        logger.error(f"❌ Error storing analytics data: {e}")
        # Don't fail the entire DAG if storage fails
        logger.warning("Continuing without data storage")

# Define the main analytics DAG
with DAG(
        'supply_chain_analytics',
        default_args=default_args,
        description='Daily supply chain analytics and reporting for VeriCrop',
        schedule_interval='0 8 * * *',  # Run daily at 8:00 AM
        catchup=False,
        tags=['vericrop', 'analytics', 'reporting'],
        max_active_runs=1
) as dag:

    extract_task = PythonOperator(
        task_id='extract_quality_data',
        python_callable=extract_quality_data,
        provide_context=True
    )

    calculate_kpis_task = PythonOperator(
        task_id='calculate_supplier_kpis',
        python_callable=calculate_supplier_kpis,
        provide_context=True
    )

    generate_report_task = PythonOperator(
        task_id='generate_daily_report',
        python_callable=generate_daily_report,
        provide_context=True
    )

    store_data_task = PythonOperator(
        task_id='store_analytics_data',
        python_callable=store_analytics_data,
        provide_context=True
    )

    send_email_report = EmailOperator(
        task_id='send_daily_report',
        to='operations@vericrop.com',
        subject='VeriCrop Daily Analytics Report - {{ ds }}',
        html_content="""
        <h2>📈 VeriCrop Daily Analytics Report</h2>
        <p>Your daily supply chain analytics report is ready.</p>
        <p>Please check the Airflow logs for the complete report.</p>
        <p><strong>Report Date:</strong> {{ ds }}</p>
        <hr>
        <p><em>Automatically generated by VeriCrop Analytics Platform</em></p>
        """,
        dag=dag
    )

    # Define task dependencies
    extract_task >> calculate_kpis_task >> [generate_report_task, store_data_task] >> send_email_report