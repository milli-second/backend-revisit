#!/usr/bin/env python3
"""
특정 사용자의 특정 날짜 데이터를 다른 날짜로 변환하는 스크립트
예: 2025-10-27 데이터를 2025-11-10으로 변환

사용 예시:
    python change_date.py --user-id USER_ID --from-date 2025-10-27 --to-date 2025-11-10
"""

import json
import argparse
from datetime import datetime, timedelta
from pathlib import Path


def change_date(user_id, from_date_str, to_date_str, input_dir=None, output_dir=None):
    """
    특정 사용자의 특정 날짜 데이터를 다른 날짜로 변환

    Args:
        user_id: 사용자 ID (해시 또는 UUID)
        from_date_str: 원본 날짜 (YYYY-MM-DD)
        to_date_str: 목표 날짜 (YYYY-MM-DD)
        input_dir: 입력 디렉토리 (기본값: daily_{user_id}_2025)
        output_dir: 출력 디렉토리 (기본값: daily_{user_id}_2025)
    """

    # 날짜 파싱
    from_date = datetime.strptime(from_date_str, "%Y-%m-%d")
    to_date = datetime.strptime(to_date_str, "%Y-%m-%d")

    # 날짜 차이 계산 (일 단위)
    date_diff = (to_date - from_date).days
    date_diff_ms = date_diff * 24 * 60 * 60 * 1000  # 밀리초로 변환

    print(f"📅 날짜 변환 정보:")
    print(f"  원본 날짜: {from_date_str}")
    print(f"  목표 날짜: {to_date_str}")
    print(f"  날짜 차이: {date_diff}일 ({'+' if date_diff > 0 else ''}{date_diff}일)\n")

    # 디렉토리 설정
    if input_dir is None:
        input_dir = f"daily_{user_id}_2025"

    if output_dir is None:
        output_dir = input_dir  # 같은 디렉토리에 저장

    input_path = Path(input_dir)
    output_path = Path(output_dir)
    output_path.mkdir(exist_ok=True)

    # 원본 파일 경로
    input_file = input_path / f"{from_date_str}.json"

    if not input_file.exists():
        print(f"❌ 오류: 파일을 찾을 수 없습니다: {input_file}")
        return

    print(f"📂 입력 파일: {input_file}")

    # 데이터 로드
    with open(input_file, 'r', encoding='utf-8') as f:
        data = json.load(f)

    print(f"📊 로드된 레코드 수: {len(data):,}\n")

    # 타임스탬프 변환
    print(f"🔄 타임스탬프 변환 중...")
    for item in data:
        # timestamp를 날짜 차이만큼 이동
        item['timestamp'] = item['timestamp'] + date_diff_ms

    # 변환 전후 샘플 출력
    if len(data) > 0:
        sample_ts = data[0]['timestamp']
        sample_dt = datetime.fromtimestamp(sample_ts / 1000.0)
        print(f"  샘플 타임스탬프: {sample_dt.strftime('%Y-%m-%d %H:%M:%S')}\n")

    # 새 파일로 저장
    output_file = output_path / f"{to_date_str}.json"
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

    print(f"✅ 변환 완료!")
    print(f"📁 출력 파일: {output_file}")
    print(f"📈 저장된 레코드 수: {len(data):,}")


def main():
    parser = argparse.ArgumentParser(
        description='특정 사용자의 특정 날짜 데이터를 다른 날짜로 변환합니다.',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
사용 예시:
  # 2025-10-27 데이터를 2025-11-10으로 변환
  python change_date.py --user-id USER_ID --from-date 2025-10-27 --to-date 2025-11-10

  # 커스텀 입력/출력 디렉토리 지정
  python change_date.py --user-id USER_ID --from-date 2025-10-27 --to-date 2025-11-10 --input-dir ./data --output-dir ./output
        """
    )

    parser.add_argument(
        '--user-id',
        required=True,
        help='사용자 ID (해시 또는 UUID)'
    )

    parser.add_argument(
        '--from-date',
        required=True,
        help='원본 날짜 (YYYY-MM-DD 형식, 예: 2025-10-27)'
    )

    parser.add_argument(
        '--to-date',
        required=True,
        help='목표 날짜 (YYYY-MM-DD 형식, 예: 2025-11-10)'
    )

    parser.add_argument(
        '--input-dir',
        help='입력 디렉토리 (기본값: daily_{user_id}_2025)'
    )

    parser.add_argument(
        '--output-dir',
        help='출력 디렉토리 (기본값: input-dir와 동일)'
    )

    args = parser.parse_args()

    change_date(
        user_id=args.user_id,
        from_date_str=args.from_date,
        to_date_str=args.to_date,
        input_dir=args.input_dir,
        output_dir=args.output_dir
    )


if __name__ == '__main__':
    main()
