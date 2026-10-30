#!/bin/sh
# Crée l'index pattern "logs-*" dans OpenSearch Dashboards et le met par défaut : Discover est prêt à l'emploi.
set -e
OPENSEARCH=http://opensearch:9200
DASHBOARDS=http://opensearch-dashboards:5601

echo "Attente d'OpenSearch..."
until curl -sf "$OPENSEARCH/_cluster/health" > /dev/null; do
  sleep 5
done

# Un seul nœud : pas de réplique, sinon les index restent en état "yellow"
curl -sf -X PUT "$OPENSEARCH/_index_template/logs" -H 'Content-Type: application/json' \
  -d '{"index_patterns": ["logs-*"], "template": {"settings": {"number_of_replicas": 0}}}' > /dev/null

# L'index pattern doit être créé après le premier log : Dashboards y enregistre alors la liste des champs
# (dont @timestamp). Créé sur un index vide, il reste sans champs et Discover échoue.
echo "Attente des premiers logs..."
tries=0
until curl -sf "$OPENSEARCH/_cat/indices/logs-*" | grep -q logs- || [ $tries -ge 60 ]; do
  tries=$((tries + 1))
  sleep 5
done

echo "Attente d'OpenSearch Dashboards..."
until curl -sf "$DASHBOARDS/api/status" > /dev/null; do
  sleep 5
done

# Liste des champs des index logs-* (ce que fait le bouton « rafraîchir les champs » de l'interface).
# L'index pattern l'enregistre sous forme de texte JSON : on garde le tableau et on échappe \ et ".
FIELDS=$(curl -sf "$DASHBOARDS/api/index_patterns/_fields_for_wildcard?pattern=logs-*" \
  | sed -e 's/^{"fields"://' -e 's/}$//' -e 's/\\/\\\\/g' -e 's/"/\\"/g')

curl -sf -X POST "$DASHBOARDS/api/saved_objects/index-pattern/logs?overwrite=true" \
  -H 'osd-xsrf: true' -H 'Content-Type: application/json' \
  -d "{\"attributes\": {\"title\": \"logs-*\", \"timeFieldName\": \"@timestamp\", \"fields\": \"$FIELDS\"}}" > /dev/null

curl -sf -X POST "$DASHBOARDS/api/opensearch-dashboards/settings" \
  -H 'osd-xsrf: true' -H 'Content-Type: application/json' \
  -d '{"changes": {"defaultIndex": "logs"}}' > /dev/null

echo "Index pattern logs-* créé"
