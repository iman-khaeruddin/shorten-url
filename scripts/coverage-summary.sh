#!/bin/bash

# Test Coverage Summary Script
# Usage: ./scripts/coverage-summary.sh

set -e

JACOCO_CSV="target/site/jacoco/jacoco.csv"
JACOCO_HTML="target/site/jacoco/index.html"

echo "🧪 AIT URL Shortener - Test Coverage Summary"
echo "=============================================="

# Check if coverage report exists
if [ ! -f "$JACOCO_CSV" ]; then
    echo "No coverage report found. Run tests first:"
    echo "   ./mvnw clean test jacoco:report"
    exit 1
fi

echo ""
echo "Coverage by Component:"
echo "-------------------------"

# Parse CSV and display coverage by package
awk -F',' '
BEGIN {
    printf "%-30s %10s %12s %10s\n", "COMPONENT", "LINES", "COVERAGE", "BRANCHES"
    printf "%-30s %10s %12s %10s\n", "----------", "-----", "--------", "--------"
}
NR > 1 {
    if ($2 != "" && $2 != "PACKAGE") {
        package = $2
        gsub("com.project.ait.", "", package)
        lines_missed = $7
        lines_covered = $8
        branch_missed = $5
        branch_covered = $6
        
        total_lines = lines_missed + lines_covered
        total_branches = branch_missed + branch_covered
        
        line_coverage = (total_lines > 0) ? int((lines_covered / total_lines) * 100) : 0
        branch_coverage = (total_branches > 0) ? int((branch_covered / total_branches) * 100) : 0
        
        printf "%-30s %10d %10d%% %8d%%\n", package, total_lines, line_coverage, branch_coverage
        
        total_lines_all += total_lines
        covered_lines_all += lines_covered
        total_branches_all += total_branches
        covered_branches_all += branch_covered
    }
}
END {
    printf "%-30s %10s %12s %10s\n", "----------", "-----", "--------", "--------"
    overall_line_coverage = (total_lines_all > 0) ? int((covered_lines_all / total_lines_all) * 100) : 0
    overall_branch_coverage = (total_branches_all > 0) ? int((covered_branches_all / total_branches_all) * 100) : 0
    printf "%-30s %10d %10d%% %8d%%\n", "OVERALL", total_lines_all, overall_line_coverage, overall_branch_coverage
}
' "$JACOCO_CSV"

echo ""
echo " Coverage Thresholds:"
echo "-----------------------"
echo "Line Coverage:   70% minimum"
echo "Branch Coverage: 60% minimum"

echo ""
echo " Report Files:"
echo "----------------"
echo "HTML Report: $JACOCO_HTML"
echo "CSV Report:  $JACOCO_CSV"
echo "XML Report:  target/site/jacoco/jacoco.xml"

echo ""
echo " Quick Commands:"
echo "------------------"
echo "Open HTML report:    open $JACOCO_HTML"
echo "View CSV in terminal: cat $JACOCO_CSV"
echo "Re-run coverage:     ./mvnw clean test jacoco:report"

# Check if HTML report exists and offer to open it
if [ -f "$JACOCO_HTML" ]; then
    echo ""
    read -p " Open HTML coverage report in browser? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        if command -v open &> /dev/null; then
            open "$JACOCO_HTML"
        elif command -v xdg-open &> /dev/null; then
            xdg-open "$JACOCO_HTML"
        else
            echo "Please open: file://$(pwd)/$JACOCO_HTML"
        fi
    fi
fi
