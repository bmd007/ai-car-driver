ENDPOINT=us-east5-aiplatform.googleapis.com
REGION=us-east5
PROJECT_ID=ai-circle-2025-cluster-34390

http POST https://${ENDPOINT}/v1/projects/${PROJECT_ID}/locations/${REGION}/endpoints/openapi/chat/completions \
  "Authorization:Bearer $(gcloud auth print-access-token)" \
  "Content-Type:application/json" \
  model="meta/llama-4-maverick-17b-128e-instruct-maas" \
  stream:=true \
  messages:='[{"role": "user", "content": "Summer travel plan to Paris"}]'
