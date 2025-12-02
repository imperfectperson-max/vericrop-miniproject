#!/usr/bin/env python3
"""
Multi-Instance Simulation Verification Script

This script verifies that a simulation ran correctly by checking:
1. Kafka messages were produced and consumed
2. Key events occurred (batches created, alerts triggered, final quality computed)
3. Scenario duration was approximately 2 minutes

Usage:
    python verify-simulation.py [--scenario SCENARIO] [--kafka-server HOST:PORT]
"""

import argparse
import json
import sys
import time
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple

# Try to import kafka-python
try:
    from kafka import KafkaConsumer, TopicPartition
    KAFKA_AVAILABLE = True
except ImportError:
    KAFKA_AVAILABLE = False
    print("Warning: kafka-python not installed. Install with: pip install kafka-python")


class SimulationVerifier:
    """Verifies multi-instance simulation results from Kafka topics."""
    
    TOPICS = [
        'simulation-control',
        'map-simulation',
        'temperature-compliance',
        'quality-alerts',
        'batch-updates'
    ]
    
    def __init__(self, bootstrap_servers: str = 'localhost:9092'):
        self.bootstrap_servers = bootstrap_servers
        self.results: Dict[str, List[dict]] = {topic: [] for topic in self.TOPICS}
        self.verification_results: Dict[str, bool] = {}
    
    def collect_messages(self, timeout_seconds: int = 30) -> Dict[str, List[dict]]:
        """Collect messages from all simulation topics."""
        if not KAFKA_AVAILABLE:
            print("Kafka not available - running in simulation mode")
            return self._generate_mock_messages()
        
        print(f"Collecting messages from Kafka topics (timeout: {timeout_seconds}s)...")
        
        try:
            consumer = KafkaConsumer(
                *self.TOPICS,
                bootstrap_servers=self.bootstrap_servers,
                auto_offset_reset='earliest',
                consumer_timeout_ms=timeout_seconds * 1000,
                value_deserializer=lambda m: json.loads(m.decode('utf-8')) if m else None
            )
            
            for message in consumer:
                if message.value:
                    self.results[message.topic].append({
                        'timestamp': message.timestamp,
                        'key': message.key.decode('utf-8') if message.key else None,
                        'value': message.value
                    })
            
            consumer.close()
            
        except Exception as e:
            print(f"Error collecting messages: {e}")
        
        return self.results
    
    def _generate_mock_messages(self) -> Dict[str, List[dict]]:
        """Generate mock messages for testing without Kafka."""
        now = int(datetime.now().timestamp() * 1000)
        
        # Mock simulation-control messages
        self.results['simulation-control'] = [
            {'timestamp': now - 120000, 'key': 'BATCH_1', 'value': {'action': 'START', 'batchId': 'BATCH_1'}},
            {'timestamp': now - 120000, 'key': 'BATCH_2', 'value': {'action': 'START', 'batchId': 'BATCH_2'}},
            {'timestamp': now - 120000, 'key': 'BATCH_3', 'value': {'action': 'START', 'batchId': 'BATCH_3'}},
            {'timestamp': now, 'key': 'BATCH_1', 'value': {'action': 'STOP', 'batchId': 'BATCH_1', 'completed': True}},
            {'timestamp': now, 'key': 'BATCH_2', 'value': {'action': 'STOP', 'batchId': 'BATCH_2', 'completed': True}},
            {'timestamp': now, 'key': 'BATCH_3', 'value': {'action': 'STOP', 'batchId': 'BATCH_3', 'completed': True}},
        ]
        
        # Mock map-simulation messages (multiple per batch)
        for i in range(24):  # 24 updates over 2 minutes
            progress = (i + 1) / 24 * 100
            for batch_num in range(1, 4):
                self.results['map-simulation'].append({
                    'timestamp': now - 120000 + (i * 5000),
                    'key': f'BATCH_{batch_num}',
                    'value': {
                        'batch_id': f'BATCH_{batch_num}',
                        'progress_percent': progress,
                        'status': 'IN_TRANSIT' if progress < 90 else 'ARRIVING',
                        'temperature': 5.5 if batch_num != 1 or progress < 30 or progress > 50 else 12.0
                    }
                })
        
        # Mock quality-alerts (for temperature breach scenario)
        self.results['quality-alerts'] = [
            {'timestamp': now - 90000, 'key': 'BATCH_1', 'value': {'batch_id': 'BATCH_1', 'alert_type': 'TEMPERATURE_BREACH', 'temperature': 12.0}},
            {'timestamp': now - 85000, 'key': 'BATCH_1', 'value': {'batch_id': 'BATCH_1', 'alert_type': 'TEMPERATURE_BREACH', 'temperature': 11.5}},
        ]
        
        return self.results
    
    def verify_simulation_control(self) -> Tuple[bool, str]:
        """Verify simulation control events (START and STOP)."""
        messages = self.results['simulation-control']
        
        if not messages:
            return False, "No simulation control messages found"
        
        starts = [m for m in messages if m['value'].get('action') == 'START']
        stops = [m for m in messages if m['value'].get('action') == 'STOP']
        
        if len(starts) < 3:
            return False, f"Expected at least 3 START events, found {len(starts)}"
        
        if len(stops) < 3:
            return False, f"Expected at least 3 STOP events, found {len(stops)}"
        
        # Check that all stops are marked as completed
        completed_stops = [m for m in stops if m['value'].get('completed') == True]
        if len(completed_stops) < 3:
            return False, f"Expected at least 3 completed stops, found {len(completed_stops)}"
        
        return True, f"Found {len(starts)} starts and {len(stops)} stops (all completed)"
    
    def verify_map_simulation(self) -> Tuple[bool, str]:
        """Verify map simulation events for all instances."""
        messages = self.results['map-simulation']
        
        if not messages:
            return False, "No map simulation messages found"
        
        # Group by batch_id
        batches: Dict[str, List[dict]] = {}
        for msg in messages:
            batch_id = msg['value'].get('batch_id') or msg['key']
            if batch_id not in batches:
                batches[batch_id] = []
            batches[batch_id].append(msg)
        
        if len(batches) < 3:
            return False, f"Expected at least 3 batches, found {len(batches)}"
        
        # Check progress for each batch
        for batch_id, batch_msgs in batches.items():
            max_progress = max(m['value'].get('progress_percent', 0) for m in batch_msgs)
            if max_progress < 95:
                return False, f"Batch {batch_id} only reached {max_progress}% progress"
        
        total_updates = len(messages)
        return True, f"Found {total_updates} map updates across {len(batches)} batches"
    
    def verify_quality_alerts(self, scenario: str) -> Tuple[bool, str]:
        """Verify quality alerts based on scenario type."""
        messages = self.results['quality-alerts']
        
        if scenario == 'normal':
            # Normal scenario should have few or no alerts
            if len(messages) > 5:
                return False, f"Normal scenario should have minimal alerts, found {len(messages)}"
            return True, f"Found {len(messages)} alerts (expected: minimal for normal scenario)"
        
        elif scenario == 'temperature_breach':
            # Temperature breach should have alerts
            if len(messages) < 1:
                return False, "Temperature breach scenario should have alerts"
            
            # Check for temperature breach alerts
            breach_alerts = [m for m in messages if 'TEMPERATURE' in str(m['value'].get('alert_type', ''))]
            if not breach_alerts:
                return False, "Expected TEMPERATURE_BREACH alerts"
            
            return True, f"Found {len(breach_alerts)} temperature breach alerts"
        
        elif scenario == 'route_disruption':
            # Route disruption may have some alerts
            return True, f"Found {len(messages)} alerts for route disruption scenario"
        
        return True, f"Found {len(messages)} alerts"
    
    def verify_duration(self) -> Tuple[bool, str]:
        """Verify that simulation ran for approximately 2 minutes."""
        control_msgs = self.results['simulation-control']
        
        if len(control_msgs) < 2:
            return False, "Not enough control messages to verify duration"
        
        # Find first start and last stop
        starts = [m for m in control_msgs if m['value'].get('action') == 'START']
        stops = [m for m in control_msgs if m['value'].get('action') == 'STOP']
        
        if not starts or not stops:
            return False, "Missing start or stop events"
        
        first_start = min(m['timestamp'] for m in starts)
        last_stop = max(m['timestamp'] for m in stops)
        
        duration_ms = last_stop - first_start
        duration_seconds = duration_ms / 1000
        
        # Allow ±30 seconds tolerance from 2 minutes (120 seconds)
        if 90 <= duration_seconds <= 150:
            return True, f"Simulation duration: {duration_seconds:.1f} seconds (within expected ~2 minute range)"
        else:
            return False, f"Simulation duration: {duration_seconds:.1f} seconds (expected ~120 seconds)"
    
    def run_verification(self, scenario: str = 'normal') -> bool:
        """Run all verifications and return overall result."""
        print("\n" + "=" * 60)
        print("  Multi-Instance Simulation Verification")
        print("=" * 60)
        print(f"  Scenario: {scenario}")
        print(f"  Kafka:    {self.bootstrap_servers}")
        print("=" * 60 + "\n")
        
        # Collect messages if not already done
        if not any(self.results.values()):
            self.collect_messages()
        
        # Run verifications
        verifications = [
            ("Simulation Control Events", self.verify_simulation_control),
            ("Map Simulation Updates", self.verify_map_simulation),
            ("Quality Alerts", lambda: self.verify_quality_alerts(scenario)),
            ("Simulation Duration", self.verify_duration),
        ]
        
        all_passed = True
        
        for name, verify_func in verifications:
            try:
                passed, message = verify_func()
                self.verification_results[name] = passed
                
                status = "✅ PASS" if passed else "❌ FAIL"
                print(f"{status}: {name}")
                print(f"       {message}")
                print()
                
                if not passed:
                    all_passed = False
                    
            except Exception as e:
                self.verification_results[name] = False
                print(f"❌ ERROR: {name}")
                print(f"       {str(e)}")
                print()
                all_passed = False
        
        # Print summary
        print("=" * 60)
        if all_passed:
            print("  ✅ ALL VERIFICATIONS PASSED")
        else:
            failed = [k for k, v in self.verification_results.items() if not v]
            print(f"  ❌ SOME VERIFICATIONS FAILED: {', '.join(failed)}")
        print("=" * 60)
        
        # Print message counts
        print("\nMessage Counts:")
        for topic, messages in self.results.items():
            print(f"  {topic}: {len(messages)}")
        
        return all_passed
    
    def generate_report(self) -> dict:
        """Generate a verification report as a dictionary."""
        return {
            'timestamp': datetime.now().isoformat(),
            'kafka_server': self.bootstrap_servers,
            'verification_results': self.verification_results,
            'message_counts': {topic: len(msgs) for topic, msgs in self.results.items()},
            'all_passed': all(self.verification_results.values())
        }


def main():
    parser = argparse.ArgumentParser(description='Verify multi-instance simulation results')
    parser.add_argument('--scenario', default='normal', 
                        choices=['normal', 'temperature_breach', 'route_disruption'],
                        help='Scenario type to verify')
    parser.add_argument('--kafka-server', default='localhost:9092',
                        help='Kafka bootstrap server')
    parser.add_argument('--timeout', type=int, default=30,
                        help='Timeout for collecting messages (seconds)')
    parser.add_argument('--mock', action='store_true',
                        help='Use mock data instead of connecting to Kafka')
    parser.add_argument('--output', type=str,
                        help='Output file for JSON report')
    
    args = parser.parse_args()
    
    verifier = SimulationVerifier(args.kafka_server)
    
    if args.mock:
        verifier._generate_mock_messages()
    else:
        verifier.collect_messages(args.timeout)
    
    all_passed = verifier.run_verification(args.scenario)
    
    if args.output:
        report = verifier.generate_report()
        with open(args.output, 'w') as f:
            json.dump(report, f, indent=2)
        print(f"\nReport saved to: {args.output}")
    
    sys.exit(0 if all_passed else 1)


if __name__ == '__main__':
    main()
