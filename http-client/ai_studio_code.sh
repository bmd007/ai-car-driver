#!/bin/bash
set -e -E

MODEL_ID="gemini-2.5-pro"
GENERATE_CONTENT_API="streamGenerateContent"

cat << EOF > request.json
{
    "contents": [
      {
        "role": "user",
        "parts": [
          {
            "text": "yo yo"
          },
        ]
      },
    ],
    "generationConfig": {
      "thinkingConfig": {
        "thinkingBudget": -1,
      },
    },
}
EOF

curl \
-X POST \
-H "Content-Type: application/json" \
"https://generativelanguage.googleapis.com/v1beta/models/${MODEL_ID}:${GENERATE_CONTENT_API}?key=${GEMINI_API_KEY}" -d '@request.json'
