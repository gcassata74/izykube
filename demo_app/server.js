const path = require('path');
const express = require('express');
const morgan = require('morgan');
const { testMySqlConnection } = require('./connection');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(morgan('dev'));
app.use(express.static(path.join(__dirname, 'public')));

console.log(`[startup] MySQL Connection Test app starting on port ${PORT}`);

app.get('/test-connection', async (req, res) => {
  console.log('[request] /test-connection invoked');
  try {
    const result = await testMySqlConnection();
    if (result.status === 'ok') {
      console.log('[success] MySQL connection succeeded');
      return res.json(result);
    }
    console.error('[error] MySQL connection failed:', result.message);
    return res.status(500).json(result);
  } catch (err) {
    console.error('[error] Unexpected failure:', err.message);
    return res.status(500).json({ status: 'error', message: err.message });
  }
});

app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`[startup] Server is listening on port ${PORT}`);
});
