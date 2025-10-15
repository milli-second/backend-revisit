#!/usr/bin/env python3
"""
스마트폰 로그 데이터셋을 사용자별로 분리하는 스크립트
"""

import json
from pathlib import Path
from collections import defaultdict

def split_by_user(input_file, output_dir):
    """사용자별로 데이터를 분리하여 저장"""

    # 출력 디렉토리 생성
    output_path = Path(output_dir)
    output_path.mkdir(exist_ok=True)

    print(f"📂 Reading {input_file}...")
    with open(input_file, 'r', encoding='utf-8') as f:
        data = json.load(f)

    print(f"✅ Loaded {len(data):,} records")

    # 사용자별로 데이터 그룹화
    print("📊 Grouping by subject_id...")
    user_data = defaultdict(list)
    for item in data:
        user_data[item['subject_id']].append(item)

    print(f"✅ Found {len(user_data)} unique users")

    # 각 사용자별로 파일 저장
    print("\n💾 Saving individual user files...")
    for i, (subject_id, records) in enumerate(user_data.items(), 1):
        output_file = output_path / f"user_{subject_id}.json"
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(records, f, indent=2, ensure_ascii=False)
        print(f"  [{i}/{len(user_data)}] {subject_id}: {len(records):,} records")

    # 요약 정보 저장
    summary = {
        "total_users": len(user_data),
        "total_records": len(data),
        "users": [
            {
                "subject_id": subject_id,
                "record_count": len(records),
                "filename": f"user_{subject_id}.json"
            }
            for subject_id, records in sorted(user_data.items(), key=lambda x: len(x[1]), reverse=True)
        ]
    }

    summary_file = output_path / "summary.json"
    with open(summary_file, 'w', encoding='utf-8') as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    print(f"\n✅ Done! Files saved to: {output_path}/")
    print(f"📄 Summary saved to: {summary_file}")

if __name__ == '__main__':
    input_file = "스마트폰_로그_데이터셋.json"
    output_dir = "users"

    split_by_user(input_file, output_dir)
