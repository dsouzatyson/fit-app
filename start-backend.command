#!/bin/bash
cd "$(dirname "$0")/backend"

echo "📦 Installing dependencies..."
npm install

echo ""
echo "🚀 Starting Fit App backend on http://localhost:3000"
echo "   Open test-ui.html in Chrome to test"
echo ""
npm run start:dev
