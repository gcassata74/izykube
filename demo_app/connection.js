const mysql = require('mysql2/promise');

function getDbConfig() {
  const {
    DB_HOST,
    DB_PORT,
    DB_NAME,
    DB_USER,
    DB_PASSWORD
  } = process.env;

  if (!DB_HOST) {
    throw new Error('DB_HOST is not set');
  }
  if (!DB_PORT) {
    throw new Error('DB_PORT is not set');
  }
  if (!DB_NAME) {
    throw new Error('DB_NAME is not set');
  }
  if (!DB_USER) {
    throw new Error('DB_USER is not set');
  }
  if (DB_PASSWORD === undefined) {
    throw new Error('DB_PASSWORD is not set');
  }

  return {
    host: DB_HOST,
    port: Number(DB_PORT),
    database: DB_NAME,
    user: DB_USER,
    password: DB_PASSWORD,
    connectTimeout: 5000
  };
}

async function testMySqlConnection() {
  let connection;
  try {
    const config = getDbConfig();
    connection = await mysql.createConnection(config);
    await connection.query('SELECT 1');
    return { status: 'ok' };
  } catch (error) {
    return { status: 'error', message: error.message };
  } finally {
    if (connection) {
      await connection.end().catch(() => {});
    }
  }
}

module.exports = {
  testMySqlConnection
};
