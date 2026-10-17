#!/usr/bin/env python3
"""
2018년 데이터를 2025년 같은 월-일로 변환하는 스크립트
예: 2018-10-17 14:30:00 -> 2025-10-17 14:30:00
"""

import argparse
import json
import uuid
from datetime import datetime
from pathlib import Path


def convert_timestamp_to_2025(old_timestamp_ms):
    """
    2018년 timestamp를 2025년 같은 월-일-시-분-초로 변환

    예: 2018-10-17 14:30:00 -> 2025-10-17 14:30:00
    """
    # 기존 timestamp를 datetime으로 변환
    old_dt = datetime.fromtimestamp(old_timestamp_ms / 1000.0)

    # 연도만 2025로 변경
    new_dt = old_dt.replace(year=2025)

    # 밀리초 단위 timestamp로 변환
    return int(new_dt.timestamp() * 1000)


def convert_all_daily_files(user_id, input_dir=None, output_dir=None):
    """모든 날짜별 파일을 2025년으로 변환"""

    if input_dir is None:
        input_dir = f"daily_{user_id}"

    if output_dir is None:
        output_dir = f"daily_{user_id}_2025"

    input_path = Path(input_dir)
    output_path = Path(output_dir)
    output_path.mkdir(exist_ok=True)

    # 해시 문자열을 UUID로 변환 (deterministic)
    user_uuid = str(uuid.uuid5(uuid.NAMESPACE_DNS, user_id))

    # 모든 JSON 파일 찾기 (summary.json 제외)
    json_files = [f for f in input_path.glob("*.json") if f.name != "summary.json"]

    print(f"📂 Found {len(json_files)} daily files to convert\n")

    summary = {
        "user_id": user_id,
        "total_files": len(json_files),
        "files": []
    }

    for json_file in sorted(json_files):
        # 날짜 추출 (2018-10-17.json -> 2018-10-17)
        date_str = json_file.stem

        # 연도를 2025로 변경 (2018-10-17 -> 2025-10-17)
        parts = date_str.split('-')
        if len(parts) == 3:
            new_date_str = f"2025-{parts[1]}-{parts[2]}"
        else:
            continue

        print(f"🔄 Converting {date_str} -> {new_date_str}...")

        # 데이터 로드
        with open(json_file, 'r', encoding='utf-8') as f:
            data = json.load(f)

        # timestamp 변환, id 필드 제거, subject_id를 UUID로 변환
        for item in data:
            item['timestamp'] = convert_timestamp_to_2025(item['timestamp'])
            # id 필드 제거
            if 'id' in item:
                del item['id']
            # subject_id를 UUID로 변환
            item['subject_id'] = user_uuid

        # 새 파일명으로 저장
        output_file = output_path / f"{new_date_str}.json"
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)

        print(f"  ✅ {len(data):,} records -> {output_file.name}\n")

        summary["files"].append({
            "original_date": date_str,
            "new_date": new_date_str,
            "record_count": len(data),
            "filename": f"{new_date_str}.json"
        })

    # 요약 저장
    summary_file = output_path / "summary.json"
    with open(summary_file, 'w', encoding='utf-8') as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    print(f"\n✅ Done! All files converted")
    print(f"📁 Output directory: {output_path}/")
    print(f"📄 Summary: {summary_file}")
    print(f"\n사용 가능한 날짜:")
    for file_info in sorted(summary["files"], key=lambda x: x["new_date"]):
        print(f"  {file_info['new_date']}: {file_info['record_count']:,} records")


def main():
    parser = argparse.ArgumentParser(description='날짜별 파일의 타임스탬프를 2025년으로 옮깁니다.')
    parser.add_argument('--user-id', required=True, help='사용자 ID (daily_{ID} 디렉토리의 ID)')
    parser.add_argument('--input-dir', help='입력 디렉토리 (기본값: daily_{user_id})')
    parser.add_argument('--output-dir', help='출력 디렉토리 (기본값: daily_{user_id}_2025)')
    args = parser.parse_args()

    convert_all_daily_files(args.user_id, input_dir=args.input_dir, output_dir=args.output_dir)


if __name__ == '__main__':
    main()
