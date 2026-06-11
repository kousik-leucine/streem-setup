-- Local SQLite store for streem-setup.
-- Holds the tool's own state: target connections + audit log of operations.
-- Never holds streem-backend domain data.

CREATE TABLE IF NOT EXISTS connections (
  id            TEXT PRIMARY KEY,           -- uuid
  name          TEXT NOT NULL UNIQUE,       -- "strides-prod", "uat-mumbai"
  host          TEXT NOT NULL,              -- when ssh tunnel: host as resolved from the bastion
  port          INTEGER NOT NULL,
  database      TEXT NOT NULL,
  username      TEXT NOT NULL,
  password_enc  BLOB NOT NULL,              -- AES-GCM ciphertext (iv prepended)
  ssl_mode      TEXT,                       -- disable | require | verify-ca | verify-full
  environment   TEXT NOT NULL,              -- LOCAL | DEV | UAT | PROD
  notes         TEXT,
  created_at    INTEGER NOT NULL,           -- epoch millis
  last_used_at  INTEGER,
  -- SSH tunnel config (nullable when use_ssh_tunnel = 0)
  use_ssh_tunnel             INTEGER NOT NULL DEFAULT 0,
  ssh_host                   TEXT,
  ssh_port                   INTEGER,
  ssh_username               TEXT,
  ssh_auth_method            TEXT,          -- PASSWORD | KEY
  ssh_password_enc           BLOB,
  ssh_private_key_path       TEXT,
  ssh_key_passphrase_enc     BLOB,
  ssh_strict_host_key_check  INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_connections_env ON connections(environment);

CREATE TABLE IF NOT EXISTS audit_log (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  connection_id  TEXT NOT NULL,
  operation      TEXT NOT NULL,             -- NEW_ORG | ADD_FACILITY | ADD_USECASE | ...
  payload_json   TEXT NOT NULL,             -- operation input
  sql_text       TEXT NOT NULL,             -- exact SQL that ran (or would have)
  status         TEXT NOT NULL,             -- PREVIEW | SUCCESS | FAILED | ROLLED_BACK
  error          TEXT,
  operator       TEXT,                      -- $USER from environment
  started_at     INTEGER NOT NULL,
  finished_at    INTEGER,
  FOREIGN KEY (connection_id) REFERENCES connections(id)
);

CREATE INDEX IF NOT EXISTS idx_audit_connection ON audit_log(connection_id);
CREATE INDEX IF NOT EXISTS idx_audit_started_at ON audit_log(started_at DESC);

-- Single-row table holding the master-password verification artifact.
-- We store salt + a known-plaintext HMAC, never the password itself.
CREATE TABLE IF NOT EXISTS master_key (
  id           INTEGER PRIMARY KEY CHECK (id = 1),
  salt         BLOB NOT NULL,
  verifier     BLOB NOT NULL,               -- HMAC-SHA256(derivedKey, "streem-setup")
  created_at   INTEGER NOT NULL
);
