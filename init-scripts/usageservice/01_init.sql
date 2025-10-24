-- TimescaleDB 확장 활성화
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Categories 테이블 생성
CREATE TABLE IF NOT EXISTS categories (
    category_id SERIAL PRIMARY KEY,
    category_code TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL
);

-- Categories 초기 데이터
INSERT INTO categories (category_code, display_name) VALUES
    ('ANDROID_WEAR', 'Android Wear'),
    ('ART_AND_DESIGN', 'Art & Design'),
    ('AUTO_AND_VEHICLES', 'Auto & Vehicles'),
    ('BEAUTY', 'Beauty'),
    ('BOOKS_AND_REFERENCE', 'Books & Reference'),
    ('BUSINESS', 'Business'),
    ('COMICS', 'Comics'),
    ('COMMUNICATION', 'Communication'),
    ('DATING', 'Dating'),
    ('EDUCATION', 'Education'),
    ('ENTERTAINMENT', 'Entertainment'),
    ('EVENTS', 'Events'),
    ('FINANCE', 'Finance'),
    ('FOOD_AND_DRINK', 'Food & Drink'),
    ('HEALTH_AND_FITNESS', 'Health & Fitness'),
    ('HOUSE_AND_HOME', 'House & Home'),
    ('LIBRARIES_AND_DEMO', 'Libraries & Demo'),
    ('LIFESTYLE', 'Lifestyle'),
    ('MAPS_AND_NAVIGATION', 'Maps & Navigation'),
    ('MEDICAL', 'Medical'),
    ('MUSIC_AND_AUDIO', 'Music & Audio'),
    ('NEWS_AND_MAGAZINES', 'News & Magazines'),
    ('PARENTING', 'Parenting'),
    ('PERSONALIZATION', 'Personalization'),
    ('PHOTOGRAPHY', 'Photography'),
    ('PRODUCTIVITY', 'Productivity'),
    ('SHOPPING', 'Shopping'),
    ('SOCIAL', 'Social'),
    ('SPORTS', 'Sports'),
    ('TOOLS', 'Tools'),
    ('TRAVEL_AND_LOCAL', 'Travel & Local'),
    ('VIDEO_PLAYERS', 'Video Players'),
    ('WATCH_FACE', 'Watch Face'),
    ('WEATHER', 'Weather'),
    ('GAME', 'Game'),
    ('FAMILY', 'Family')
ON CONFLICT (category_code) DO NOTHING;

-- Raw Data 테이블 생성 (TimescaleDB hypertable)
CREATE TABLE IF NOT EXISTS raw_data (
    subject_id UUID NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    utcoffset INTEGER,
    source_type TEXT,
    source_info TEXT,
    package_name TEXT NOT NULL,
    name TEXT,
    is_system_app INTEGER,
    is_updated_system_app INTEGER,
    type TEXT NOT NULL,  -- 이벤트 타입 (USER_INTERACTION, MOVE_TO_FOREGROUND 등)
    PRIMARY KEY (subject_id, timestamp)
);

-- Hypertable로 변환
SELECT create_hypertable('raw_data', 'timestamp', if_not_exists => TRUE);

-- Daily User Metrics 테이블 생성
CREATE TABLE IF NOT EXISTS daily_user_metrics (
    subject_id UUID NOT NULL,
    summary_date DATE NOT NULL,
    total_usage_minutes INTEGER,
    dawn_minutes INTEGER,
    morning_minutes INTEGER,
    afternoon_minutes INTEGER,
    evening_minutes INTEGER,
    dawn_sns_minutes INTEGER,
    most_active_hour_start INTEGER,
    daily_pickup_count INTEGER,
    game_ratio FLOAT,
    social_comm_ratio FLOAT,
    learning_ratio FLOAT,
    entertainment_ratio FLOAT,
    PRIMARY KEY (subject_id, summary_date)
);

-- 날짜 기준 인덱스 추가
CREATE INDEX IF NOT EXISTS idx_daily_metrics_date ON daily_user_metrics(summary_date);
CREATE INDEX IF NOT EXISTS idx_daily_metrics_subject ON daily_user_metrics(subject_id);

-- Daily Category Usage 테이블 생성
CREATE TABLE IF NOT EXISTS daily_category_usage (
    subject_id UUID NOT NULL,
    summary_date DATE NOT NULL,
    category_id INTEGER NOT NULL,
    usage_minutes INTEGER,
    PRIMARY KEY (subject_id, summary_date, category_id)
);

CREATE INDEX IF NOT EXISTS idx_daily_category_date ON daily_category_usage(summary_date);
CREATE INDEX IF NOT EXISTS idx_daily_category_subject ON daily_category_usage(subject_id);

-- User SNS Insight 테이블 생성
CREATE TABLE IF NOT EXISTS user_sns_insight (
    subject_id UUID NOT NULL,
    calculated_date DATE NOT NULL,
    sns_usage_rate FLOAT,        -- 전체 사용시간 중 SNS 비율 (%)
    dawn_access_rate FLOAT,      -- SNS 사용시간 중 새벽 접속 비율 (%)
    total_sns_minutes INTEGER,   -- SNS 총 사용시간 (분)
    PRIMARY KEY (subject_id, calculated_date)
);

CREATE INDEX IF NOT EXISTS idx_sns_insight_date ON user_sns_insight(calculated_date);
CREATE INDEX IF NOT EXISTS idx_sns_insight_subject ON user_sns_insight(subject_id);

