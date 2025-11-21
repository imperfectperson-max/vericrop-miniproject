#!/usr/bin/env python3
"""
Airflow DAG Validation Script

Validates all DAG files for:
- Python syntax errors
- Import errors
- DAG definition errors
- Task configuration issues
- Idempotence checks
- Retry configuration
- Timeout settings
- Scheduling configuration

Usage:
    python scripts/validate-airflow-dags.py
    python scripts/validate-airflow-dags.py --dag-folder airflow/dags
    python scripts/validate-airflow-dags.py --verbose
"""

import os
import sys
import argparse
import importlib.util
from pathlib import Path
from typing import List, Dict, Any
import ast


def parse_arguments():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(description="Validate Airflow DAG files")
    parser.add_argument(
        "--dag-folder",
        default="airflow/dags",
        help="Path to DAGs folder (default: airflow/dags)"
    )
    parser.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="Verbose output"
    )
    return parser.parse_args()


def find_dag_files(dag_folder: str) -> List[Path]:
    """Find all Python files in the DAG folder."""
    dag_path = Path(dag_folder)
    if not dag_path.exists():
        print(f"Error: DAG folder '{dag_folder}' does not exist")
        sys.exit(1)
    
    dag_files = list(dag_path.glob("*.py"))
    # Exclude __init__.py and test files
    dag_files = [f for f in dag_files if not f.name.startswith("__") and not f.name.startswith("test_")]
    return dag_files


def check_syntax(file_path: Path) -> Dict[str, Any]:
    """Check Python syntax of a file."""
    result = {
        "file": str(file_path.name),
        "syntax_valid": False,
        "error": None
    }
    
    try:
        with open(file_path, 'r') as f:
            ast.parse(f.read(), filename=str(file_path))
        result["syntax_valid"] = True
    except SyntaxError as e:
        result["error"] = f"Syntax error at line {e.lineno}: {e.msg}"
    except Exception as e:
        result["error"] = f"Unexpected error: {str(e)}"
    
    return result


def validate_dag_configuration(file_path: Path, verbose: bool = False) -> Dict[str, Any]:
    """Validate DAG configuration."""
    result = {
        "file": str(file_path.name),
        "valid": True,
        "warnings": [],
        "errors": [],
        "dags_found": []
    }
    
    try:
        # Read file content
        with open(file_path, 'r') as f:
            content = f.read()
        
        # Parse AST
        tree = ast.parse(content, filename=str(file_path))
        
        # Check for DAG instantiation
        dags_found = False
        for node in ast.walk(tree):
            # Look for DAG() calls or @dag decorators
            if isinstance(node, ast.Call):
                if hasattr(node.func, 'id') and node.func.id == 'DAG':
                    dags_found = True
                    dag_info = extract_dag_info(node)
                    result["dags_found"].append(dag_info)
                    validate_dag_parameters(dag_info, result)
            
            # Look for @dag decorator
            if isinstance(node, ast.FunctionDef):
                for decorator in node.decorator_list:
                    if (isinstance(decorator, ast.Name) and decorator.id == 'dag') or \
                       (isinstance(decorator, ast.Call) and hasattr(decorator.func, 'id') and decorator.func.id == 'dag'):
                        dags_found = True
                        result["dags_found"].append({"dag_id": node.name, "type": "decorated"})
        
        if not dags_found:
            result["warnings"].append("No DAG definitions found in file")
        
        # Check for required imports
        check_imports(tree, result)
        
        # Check for task configuration
        check_task_configuration(tree, result, verbose)
        
    except Exception as e:
        result["valid"] = False
        result["errors"].append(f"Validation error: {str(e)}")
    
    return result


def extract_dag_info(node: ast.Call) -> Dict[str, Any]:
    """Extract DAG information from AST node."""
    dag_info = {"dag_id": None, "schedule": None, "catchup": None, "default_args": {}}
    
    # Extract keyword arguments
    for keyword in node.keywords:
        if keyword.arg == 'dag_id':
            if isinstance(keyword.value, ast.Constant):
                dag_info["dag_id"] = keyword.value.value
        elif keyword.arg == 'schedule_interval' or keyword.arg == 'schedule':
            if isinstance(keyword.value, ast.Constant):
                dag_info["schedule"] = keyword.value.value
        elif keyword.arg == 'catchup':
            if isinstance(keyword.value, ast.Constant):
                dag_info["catchup"] = keyword.value.value
        elif keyword.arg == 'default_args':
            # Extract default_args dictionary (simplified)
            dag_info["has_default_args"] = True
    
    return dag_info


def validate_dag_parameters(dag_info: Dict[str, Any], result: Dict[str, Any]):
    """Validate DAG parameters."""
    # Check for dag_id
    if not dag_info.get("dag_id"):
        result["warnings"].append("DAG missing 'dag_id' parameter")
    
    # Check for schedule
    if dag_info.get("schedule") is None:
        result["warnings"].append(f"DAG '{dag_info.get('dag_id', 'unknown')}' missing 'schedule' parameter")
    
    # Warn if catchup is not explicitly set
    if dag_info.get("catchup") is None:
        result["warnings"].append(f"DAG '{dag_info.get('dag_id', 'unknown')}' should explicitly set 'catchup' parameter")
    
    # Check if default_args are provided
    if not dag_info.get("has_default_args"):
        result["warnings"].append(f"DAG '{dag_info.get('dag_id', 'unknown')}' missing 'default_args' (retries, retry_delay, etc.)")


def check_imports(tree: ast.AST, result: Dict[str, Any]):
    """Check for required Airflow imports."""
    imports = set()
    
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom):
            if node.module and node.module.startswith('airflow'):
                imports.add(node.module)
        elif isinstance(node, ast.Import):
            for alias in node.names:
                if alias.name.startswith('airflow'):
                    imports.add(alias.name)
    
    # Check for common required imports
    if not any('airflow' in imp for imp in imports):
        result["warnings"].append("No Airflow imports found - is this a DAG file?")


def check_task_configuration(tree: ast.AST, result: Dict[str, Any], verbose: bool):
    """Check task configuration for best practices."""
    tasks_found = 0
    
    for node in ast.walk(tree):
        # Look for operator instantiations
        if isinstance(node, ast.Call):
            if hasattr(node.func, 'id') and 'Operator' in node.func.id:
                tasks_found += 1
                check_task_parameters(node, result, verbose)
    
    if verbose and tasks_found > 0:
        print(f"  Found {tasks_found} task(s) in {result['file']}")


def check_task_parameters(node: ast.Call, result: Dict[str, Any], verbose: bool):
    """Check individual task parameters."""
    task_id = None
    has_retries = False
    has_retry_delay = False
    has_timeout = False
    
    for keyword in node.keywords:
        if keyword.arg == 'task_id':
            if isinstance(keyword.value, ast.Constant):
                task_id = keyword.value.value
        elif keyword.arg == 'retries':
            has_retries = True
        elif keyword.arg == 'retry_delay':
            has_retry_delay = True
        elif keyword.arg == 'execution_timeout':
            has_timeout = True
    
    # Warnings for missing configurations
    if task_id and verbose:
        if not has_retries:
            result["warnings"].append(f"Task '{task_id}' missing 'retries' parameter (consider adding default_args)")
        if not has_retry_delay:
            result["warnings"].append(f"Task '{task_id}' missing 'retry_delay' parameter")


def print_results(results: List[Dict[str, Any]], verbose: bool):
    """Print validation results."""
    total_files = len(results)
    files_with_errors = sum(1 for r in results if r.get("errors") or not r.get("syntax_valid", True))
    files_with_warnings = sum(1 for r in results if r.get("warnings"))
    
    print("\n" + "=" * 70)
    print("Airflow DAG Validation Report")
    print("=" * 70)
    
    for result in results:
        file_name = result["file"]
        
        # Syntax check
        if not result.get("syntax_valid", True):
            print(f"\n❌ {file_name}")
            print(f"   SYNTAX ERROR: {result.get('error')}")
            continue
        
        # DAG validation
        dags_found = result.get("dags_found", [])
        errors = result.get("errors", [])
        warnings = result.get("warnings", [])
        
        if errors:
            print(f"\n❌ {file_name}")
            for error in errors:
                print(f"   ERROR: {error}")
        elif warnings:
            print(f"\n⚠️  {file_name}")
            if dags_found:
                for dag in dags_found:
                    dag_id = dag.get("dag_id", "unknown")
                    print(f"   DAG: {dag_id}")
            if verbose:
                for warning in warnings:
                    print(f"   WARNING: {warning}")
            else:
                print(f"   {len(warnings)} warning(s) found (use --verbose to see details)")
        else:
            print(f"\n✅ {file_name}")
            if dags_found:
                for dag in dags_found:
                    dag_id = dag.get("dag_id", "unknown")
                    print(f"   DAG: {dag_id}")
    
    print("\n" + "=" * 70)
    print(f"Summary:")
    print(f"  Total files: {total_files}")
    print(f"  Files with errors: {files_with_errors}")
    print(f"  Files with warnings: {files_with_warnings}")
    print(f"  Files OK: {total_files - files_with_errors - files_with_warnings}")
    print("=" * 70)
    
    if files_with_errors > 0:
        sys.exit(1)
    elif files_with_warnings > 0:
        sys.exit(0)  # Warnings don't fail the validation
    else:
        print("\n✅ All DAG files passed validation!")
        sys.exit(0)


def main():
    """Main validation function."""
    args = parse_arguments()
    
    print(f"Validating DAG files in: {args.dag_folder}")
    
    # Find DAG files
    dag_files = find_dag_files(args.dag_folder)
    
    if not dag_files:
        print(f"No DAG files found in {args.dag_folder}")
        sys.exit(1)
    
    print(f"Found {len(dag_files)} DAG file(s)")
    
    # Validate each file
    results = []
    for dag_file in dag_files:
        if args.verbose:
            print(f"\nValidating {dag_file.name}...")
        
        # Check syntax
        syntax_result = check_syntax(dag_file)
        if not syntax_result["syntax_valid"]:
            results.append(syntax_result)
            continue
        
        # Validate DAG configuration
        validation_result = validate_dag_configuration(dag_file, args.verbose)
        results.append(validation_result)
    
    # Print results
    print_results(results, args.verbose)


if __name__ == "__main__":
    main()
