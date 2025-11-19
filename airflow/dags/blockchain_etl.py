from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.python import BranchPythonOperator
from airflow.operators.dummy import DummyOperator
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
    'retries': 2,
    'retry_delay': timedelta(minutes=3)
}

def extract_blockchain_data(**context):
    """Extract data from blockchain for analytics"""
    try:
        # Simulate blockchain data extraction
        # In production, this would connect to your blockchain service
        blockchain_data = {
            'total_blocks': 145,
            'total_transactions': 892,
            'latest_block_hash': '0xabc123...',
            'chain_valid': True,
            'blocks_today': 12,
            'transactions_today': 67
        }

        # Simulate transaction data
        recent_transactions = [
            {
                'block_index': 144,
                'transaction_type': 'CREATE_BATCH',
                'batch_id': 'BATCH_001',
                'participant': 'Farmer_John',
                'timestamp': datetime.now().isoformat()
            },
            {
                'block_index': 143,
                'transaction_type': 'QUALITY_CHECK',
                'batch_id': 'BATCH_001',
                'participant': 'AI_Quality_Scanner',
                'timestamp': (datetime.now() - timedelta(hours=1)).isoformat()
            }
        ]

        context['task_instance'].xcom_push(key='blockchain_stats', value=blockchain_data)
        context['task_instance'].xcom_push(key='recent_transactions', value=recent_transactions)

        logger.info(f"✅ Extracted blockchain data: {blockchain_data['total_blocks']} blocks")
        return 'transform_blockchain_data'

    except Exception as e:
        logger.error(f"❌ Error extracting blockchain data: {e}")
        return 'extraction_failed'

def transform_blockchain_data(**context):
    """Transform blockchain data for analytics"""
    try:
        blockchain_stats = context['task_instance'].xcom_pull(task_ids='extract_blockchain_data', key='blockchain_stats')
        transactions = context['task_instance'].xcom_pull(task_ids='extract_blockchain_data', key='recent_transactions')

        # Transform data for analytics
        transformed_data = {
            'date': datetime.now().date().isoformat(),
            'total_blocks': blockchain_stats.get('total_blocks', 0),
            'total_transactions': blockchain_stats.get('total_transactions', 0),
            'blocks_today': blockchain_stats.get('blocks_today', 0),
            'transactions_today': blockchain_stats.get('transactions_today', 0),
            'chain_valid': blockchain_stats.get('chain_valid', False),
            'transaction_types': {}
        }

        # Count transaction types
        for tx in transactions:
            tx_type = tx.get('transaction_type', 'UNKNOWN')
            transformed_data['transaction_types'][tx_type] = transformed_data['transaction_types'].get(tx_type, 0) + 1

        context['task_instance'].xcom_push(key='transformed_data', value=transformed_data)
        logger.info(f"✅ Transformed blockchain data: {transformed_data}")

    except Exception as e:
        logger.error(f"❌ Error transforming blockchain data: {e}")
        raise

def load_blockchain_analytics(**context):
    """Load transformed data to analytics database"""
    try:
        transformed_data = context['task_instance'].xcom_pull(task_ids='transform_blockchain_data', key='transformed_data')

        if transformed_data:
            # Create DataFrame
            df = pd.DataFrame([transformed_data])

            # Store in SQLite (use PostgreSQL in production)
            engine = create_engine('sqlite:////opt/airflow/logs/vericrop_blockchain.db')
            df.to_sql('blockchain_analytics', engine, if_exists='append', index=False)

            logger.info(f"✅ Loaded blockchain analytics data: {len(df)} records")

            # Also store transaction type breakdown
            tx_types = transformed_data.get('transaction_types', {})
            if tx_types:
                tx_df = pd.DataFrame([
                    {'date': datetime.now().date(), 'transaction_type': k, 'count': v}
                    for k, v in tx_types.items()
                ])
                tx_df.to_sql('transaction_types', engine, if_exists='append', index=False)
                logger.info(f"✅ Loaded transaction type data: {len(tx_df)} types")

        else:
            logger.warning("No transformed data to load")

    except Exception as e:
        logger.error(f"❌ Error loading blockchain analytics: {e}")
        raise

def validate_blockchain_integrity(**context):
    """Validate blockchain integrity"""
    try:
        # Simulate blockchain validation
        # In production, this would run your blockchain validation logic
        is_valid = True  # Simulated result

        if is_valid:
            logger.info("✅ Blockchain integrity validated - chain is valid")
            return 'chain_valid'
        else:
            logger.error("❌ Blockchain integrity check failed - chain is invalid")
            return 'chain_invalid'

    except Exception as e:
        logger.error(f"❌ Error validating blockchain integrity: {e}")
        return 'validation_failed'

def handle_invalid_chain(**context):
    """Handle blockchain validation failures"""
    logger.error("🚨 BLOCKCHAIN INTEGRITY COMPROMISED - ALERTING ADMINISTRATORS")
    # In production: send critical alerts, stop processing, etc.
    # For now, just log the issue

with DAG(
        'blockchain_etl',
        default_args=default_args,
        description='ETL pipeline for blockchain data analytics',
        schedule_interval=timedelta(hours=2),  # Run every 2 hours
        catchup=False,
        tags=['vericrop', 'blockchain', 'etl', 'analytics']
) as dag:

    start = DummyOperator(task_id='start')

    extract_data = BranchPythonOperator(
        task_id='extract_blockchain_data',
        python_callable=extract_blockchain_data,
        provide_context=True
    )

    transform_data = PythonOperator(
        task_id='transform_blockchain_data',
        python_callable=transform_blockchain_data,
        provide_context=True
    )

    load_data = PythonOperator(
        task_id='load_blockchain_analytics',
        python_callable=load_blockchain_analytics,
        provide_context=True
    )

    validate_chain = BranchPythonOperator(
        task_id='validate_blockchain_integrity',
        python_callable=validate_blockchain_integrity,
        provide_context=True
    )

    chain_valid = DummyOperator(task_id='chain_valid')
    chain_invalid = PythonOperator(
        task_id='handle_invalid_chain',
        python_callable=handle_invalid_chain,
        provide_context=True
    )

    validation_failed = DummyOperator(task_id='validation_failed')
    extraction_failed = DummyOperator(task_id='extraction_failed')

    end = DummyOperator(task_id='end')

    # Define workflow
    start >> extract_data
    extract_data >> [transform_data, extraction_failed]
    transform_data >> load_data
    load_data >> validate_chain
    validate_chain >> [chain_valid, chain_invalid, validation_failed]
    [chain_valid, chain_invalid, validation_failed, extraction_failed] >> end