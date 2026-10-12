-- Tabelas de controle (data_model.md 6).

-- Transactional outbox: permanente (os enviados ficam com status SENT).
CREATE TABLE outbox_events (
    sequence     BIGSERIAL PRIMARY KEY,
    event_id     UUID         NOT NULL UNIQUE,
    aggregate_id UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      JSONB        NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts     INTEGER      NOT NULL DEFAULT 0,
    last_error   TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at      TIMESTAMPTZ
);

COMMENT ON COLUMN outbox_events.aggregate_id IS 'orderId - MessageGroupId da fila FIFO';
COMMENT ON COLUMN outbox_events.event_id IS 'eventId do envelope e MessageDeduplicationId';
COMMENT ON COLUMN outbox_events.payload IS 'Envelope JSON completo enviado ao SQS';

CREATE INDEX idx_outbox_events_pending ON outbox_events (sequence) WHERE status = 'PENDING';

-- Comandos processados (deduplicacao por idempotencyKey = transactionId:STEP).
-- Retencao de 30 dias (job diario).
CREATE TABLE inbox_events (
    message_key      VARCHAR(200) PRIMARY KEY,
    response_payload JSONB,
    processed_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON COLUMN inbox_events.response_payload IS 'Resposta publicada no processamento original; regravada no outbox em reentrega';

CREATE INDEX idx_inbox_events_processed_at ON inbox_events (processed_at);

-- Idempotencia de POST /stock/entry. Retencao de 7 dias.
CREATE TABLE idempotency_requests (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    request_hash    VARCHAR(64) NOT NULL,
    response_status INTEGER     NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_idempotency_requests_created_at ON idempotency_requests (created_at);
