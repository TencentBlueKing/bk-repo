#!/bin/bash
echo "================="
extractcode #{[inputFile]} && echo "----Extractcode complete------"
scancode --license-score 100 --license --max-depth 4 -n 4 --only-findings --json #{[resultFile]} #{[inputFile]}-extract && echo "----Scan complete-------------"
