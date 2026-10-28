#!/bin/sh
# Crée l'index pattern "logs-*" dans OpenSearch Dashboards et le met par défaut : Discover est prêt à l'emploi.
set -e
OPENSEARCH=http://opensearch:9200
DASHBOARDS=http://opensearch-dashboards:5601

# Un seul nœud : pas de réplique, sinon les index restent en état "yellow"
curl -sf -X PUT "$OPENSEARCH/_index_template/logs" -H 'Content-Type: application/json' \
  -d '{"index_patterns": ["logs-*"], "template": {"settings": {"number_of_replicas": 0}}}' > /dev/null

echo "Attente d'OpenSearch Dashboards..."
until curl -sf "$DASHBOARDS/api/status" > /dev/null; do
  sleep 5
done

curl -sf -X POST "$DASHBOARDS/api/saved_objects/index-pattern/logs?overwrite=true" \
  -H 'osd-xsrf: true' -H 'Content-Type: application/json' \
  -d '{"attributes": {"title": "logs-*", "timeFieldName": "@timestamp"}}' > /dev/null

curl -sf -X POST "$DASHBOARDS/api/opensearch-dashboards/settings" \
  -H 'osd-xsrf: true' -H 'Content-Type: application/json' \
  -d '{"changes": {"defaultIndex": "logs"}}' > /dev/null

echo "Index pattern logs-* créé"
