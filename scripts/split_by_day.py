#!/usr/bin/env python3
"""
특정 사용자의 데이터를 날짜별로 분리하는 스크립트
"""

import json
from pathlib import Path
from datetime import datetime
from collections import defaultdict

def split_by_day(user_id, input_dir="users", output_dir=None):
    """사용자 데이터를 날짜별로 분리"""

    if output_dir is None:
        output_dir = f"daily_{user_id}"

    # 출력 디렉토리 생성
    output_path = Path(output_dir)
    output_path.mkdir(exist_ok=True)

    # 사용자 데이터 로드
    input_file = Path(input_dir) / f"user_{user_id}.json"
    print(f"📂 Reading {input_file}...")

    with open(input_file, 'r', encoding='utf-8') as f:
        data = json.load(f)

    print(f"✅ Loaded {len(data):,} records")

    # 날짜별로 그룹화
    print("📊 Grouping by date...")
    daily_data = defaultdict(list)

    for item in data:
        timestamp_ms = item['timestamp']
        dt = datetime.fromtimestamp(timestamp_ms / 1000.0)
        date_str = dt.strftime('%Y-%m-%d')
        daily_data[date_str].append(item)

    print(f"✅ Found {len(daily_data)} days")

    # 각 날짜별로 파일 저장
    print("\n💾 Saving daily files...")
    summary = {
        "user_id": user_id,
        "total_records": len(data),
        "total_days": len(daily_data),
        "days": []
    }

    for date_str in sorted(daily_data.keys()):
        records = daily_data[date_str]
        output_file = output_path / f"{date_str}.json"

        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(records, f, indent=2, ensure_ascii=False)

        print(f"  {date_str}: {len(records):,} records")

        summary["days"].append({
            "date": date_str,
            "record_count": len(records),
            "filename": f"{date_str}.json"
        })

    # 요약 저장
    summary_file = output_path / "summary.json"
    with open(summary_file, 'w', encoding='utf-8') as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    print(f"\n✅ Done! Files saved to: {output_path}/")
    print(f"📄 Summary saved to: {summary_file}")

if __name__ == '__main__':
    # 가장 데이터가 많은 사용자
    user_id = "07a76922a3af962fd790fda624b7c9f1"
    split_by_day(user_id)
