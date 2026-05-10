-- TiDB DDL for Phase 2 production readiness
-- Auto-executed by spring.sql.init on startup

CREATE TABLE IF NOT EXISTS trade_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trade_id VARCHAR(64) NOT NULL COMMENT 'unique trade identifier',
    symbol VARCHAR(32) NOT NULL COMMENT 'trading pair',
    side VARCHAR(8) NOT NULL COMMENT 'buy/sell',
    price DECIMAL(24,8) NOT NULL,
    quantity DECIMAL(24,8) NOT NULL,
    amount DECIMAL(24,8) NOT NULL COMMENT 'price * quantity',
    fee DECIMAL(24,8) NOT NULL DEFAULT 0,
    fee_currency VARCHAR(16) NOT NULL DEFAULT 'USDT',
    exchange VARCHAR(32) NOT NULL COMMENT 'exchange code',
    trade_time BIGINT NOT NULL COMMENT 'trade timestamp in ms',
    created_at BIGINT NOT NULL,
    INDEX idx_symbol_time (symbol, trade_time),
    INDEX idx_exchange_time (exchange, trade_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='trade fill log';

CREATE TABLE IF NOT EXISTS order_book_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    exchange VARCHAR(32) NOT NULL,
    bids TEXT COMMENT 'JSON: [["price","qty"],...]',
    asks TEXT COMMENT 'JSON: [["price","qty"],...]',
    bid_count INT DEFAULT 0,
    ask_count INT DEFAULT 0,
    snapshot_time BIGINT NOT NULL COMMENT 'snapshot timestamp in ms',
    created_at BIGINT NOT NULL,
    INDEX idx_symbol_exchange_time (symbol, exchange, snapshot_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='periodic order book depth snapshots';

CREATE TABLE IF NOT EXISTS inventory_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    exchange VARCHAR(32) NOT NULL,
    net_position DECIMAL(24,8) NOT NULL DEFAULT 0,
    entry_price DECIMAL(24,8) DEFAULT NULL,
    realized_pnl DECIMAL(24,8) NOT NULL DEFAULT 0,
    total_fees DECIMAL(24,8) NOT NULL DEFAULT 0,
    total_volume DECIMAL(24,8) NOT NULL DEFAULT 0,
    trade_count INT NOT NULL DEFAULT 0,
    snapshot_time BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE KEY uk_symbol_exchange (symbol, exchange)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='inventory and PnL snapshot per symbol/exchange';

CREATE TABLE IF NOT EXISTS fee_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL DEFAULT '*' COMMENT 'specific symbol or * for all',
    taker_rate DECIMAL(10,6) NOT NULL COMMENT 'taker fee rate, e.g. 0.001',
    maker_rate DECIMAL(10,6) NOT NULL COMMENT 'maker fee rate',
    fee_currency VARCHAR(16) NOT NULL DEFAULT 'USDT',
    updated_at BIGINT NOT NULL,
    UNIQUE KEY uk_exchange_symbol (exchange, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='fee rate configuration';

-- Seed fee config for exchanges that don't provide fee in stream data
MERGE INTO fee_config (exchange, symbol, taker_rate, maker_rate, fee_currency, updated_at) VALUES
    ('BYBIT', '*', 0.001000, 0.001000, 'USDT', UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000),
    ('BINANCE', '*', 0.001000, 0.001000, 'USDT', UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000),
    ('BITGET', '*', 0.001000, 0.001000, 'USDT', UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000),
    ('OSL_GLOBAL', '*', 0.001000, 0.001000, 'USDT', UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000)
ON DUPLICATE KEY UPDATE taker_rate = VALUES(taker_rate), maker_rate = VALUES(maker_rate), updated_at = VALUES(updated_at);

-- ML training dataset
CREATE TABLE IF NOT EXISTS train_dataset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    features_json TEXT NOT NULL COMMENT 'JSON array of feature values',
    label DOUBLE DEFAULT NULL COMMENT 'future N-period return, NULL until generated',
    captured_at BIGINT NOT NULL COMMENT 'timestamp when features were captured',
    created_at BIGINT NOT NULL,
    INDEX idx_symbol_labeled (symbol, label, captured_at),
    INDEX idx_symbol_captured (symbol, captured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ML training data: features + labels';

-- ML model versions
CREATE TABLE IF NOT EXISTS model_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    hyperparameters_json TEXT NOT NULL COMMENT 'JSON: n_trees, max_depth, min_samples_leaf, feature_ratio',
    metrics_json TEXT COMMENT 'JSON: r_squared, mae, rmse',
    model_data_json MEDIUMTEXT NOT NULL COMMENT 'JSON: full RandomForest serialization',
    active TINYINT(1) NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    activated_at BIGINT DEFAULT NULL,
    INDEX idx_symbol_active (symbol, active),
    INDEX idx_symbol_created (symbol, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ML model versions and metadata';

-- Backtest results
CREATE TABLE IF NOT EXISTS backtest_result (
    id VARCHAR(32) PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    model VARCHAR(32) NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT NOT NULL,
    total_ticks INT NOT NULL DEFAULT 0,
    initial_capital DECIMAL(24,8) NOT NULL,
    final_balance DECIMAL(24,8) NOT NULL,
    total_return DECIMAL(10,4) NOT NULL DEFAULT 0,
    annualized_return DECIMAL(10,4) DEFAULT NULL,
    sharpe_ratio DECIMAL(10,4) DEFAULT NULL,
    calmar_ratio DECIMAL(10,4) DEFAULT NULL,
    max_drawdown DECIMAL(10,4) NOT NULL DEFAULT 0,
    total_trades INT NOT NULL DEFAULT 0,
    winning_trades INT NOT NULL DEFAULT 0,
    losing_trades INT NOT NULL DEFAULT 0,
    win_rate DECIMAL(6,2) DEFAULT NULL,
    profit_factor DECIMAL(10,4) DEFAULT NULL,
    total_fees DECIMAL(24,8) NOT NULL DEFAULT 0,
    config_json JSON COMMENT 'full BacktestConfig serialization',
    equity_curve_json MEDIUMTEXT COMMENT 'JSON array of equity values',
    created_at BIGINT NOT NULL,
    INDEX idx_symbol (symbol),
    INDEX idx_model (model),
    INDEX idx_sharpe (sharpe_ratio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='backtest run results';
