#!/usr/bin/env python3
"""
시간대별 앱 사용량을 시각화하는 스크립트
"""

import argparse
import json
from datetime import datetime
from collections import defaultdict, Counter
from pathlib import Path
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm

# 한글 폰트 설정
plt.rcParams['font.family'] = 'AppleGothic'  # macOS
plt.rcParams['axes.unicode_minus'] = False  # 마이너스 기호 깨짐 방지


def load_daily_data(json_file_path):
    """JSON 파일에서 일일 데이터 로드"""
    with open(json_file_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def analyze_hourly_usage(data):
    """시간대별 사용량 분석 (실제 사용 시간 계산)"""

    # 시간대별 총 이벤트 수
    hourly_events = defaultdict(int)

    # 시간대별 포그라운드 세션 수
    hourly_foreground = defaultdict(int)

    # 시간대별 실제 사용 시간 (분)
    hourly_usage_minutes = defaultdict(float)

    # 시간대별 앱별 사용 시간 (분)
    hourly_app_usage = defaultdict(lambda: defaultdict(float))

    # 데이터를 timestamp로 정렬
    sorted_data = sorted(data, key=lambda x: x['timestamp'])

    # 앱별 활성 세션 추적 {package_name: {'start_time': timestamp, 'start_hour': hour}}
    active_sessions = {}

    for item in sorted_data:
        timestamp_ms = item['timestamp']
        dt = datetime.fromtimestamp(timestamp_ms / 1000.0)
        hour = dt.hour
        package_name = item['packageName']
        app_name = item['name']
        event_type = item['type']

        # 전체 이벤트 카운트
        hourly_events[hour] += 1

        if event_type == 'MOVE_TO_FOREGROUND':
            # 기존 세션이 있으면 종료 처리
            if package_name in active_sessions:
                session = active_sessions[package_name]
                start_time = session['start_time']
                usage_millis = timestamp_ms - start_time
                usage_minutes = usage_millis / 60000.0

                # 시작 시간의 hour에 사용량 누적
                start_hour = session['start_hour']
                hourly_usage_minutes[start_hour] += usage_minutes
                hourly_app_usage[start_hour][session['app_name']] += usage_minutes

            # 새 포그라운드 세션 시작
            hourly_foreground[hour] += 1
            active_sessions[package_name] = {
                'start_time': timestamp_ms,
                'start_hour': hour,
                'app_name': app_name
            }

        elif event_type == 'MOVE_TO_BACKGROUND':
            # 세션 종료 및 사용 시간 계산
            if package_name in active_sessions:
                session = active_sessions[package_name]
                start_time = session['start_time']
                usage_millis = timestamp_ms - start_time
                usage_minutes = usage_millis / 60000.0

                # 시작 시간의 hour에 사용량 누적
                start_hour = session['start_hour']
                hourly_usage_minutes[start_hour] += usage_minutes
                hourly_app_usage[start_hour][session['app_name']] += usage_minutes

                # 세션 제거
                del active_sessions[package_name]

    # 아직 종료되지 않은 세션 처리 (데이터의 마지막 timestamp를 종료 시간으로 간주)
    if sorted_data and active_sessions:
        last_timestamp = sorted_data[-1]['timestamp']
        for package_name, session in active_sessions.items():
            start_time = session['start_time']
            usage_millis = last_timestamp - start_time
            usage_minutes = usage_millis / 60000.0

            start_hour = session['start_hour']
            hourly_usage_minutes[start_hour] += usage_minutes
            hourly_app_usage[start_hour][session['app_name']] += usage_minutes

    return hourly_events, hourly_foreground, hourly_usage_minutes, hourly_app_usage


def plot_hourly_activity(hourly_events, hourly_foreground, hourly_usage_minutes, date_str):
    """시간대별 총 활동량 그래프"""
    hours = range(24)
    events = [hourly_events.get(h, 0) for h in hours]
    foregrounds = [hourly_foreground.get(h, 0) for h in hours]
    usage_minutes = [hourly_usage_minutes.get(h, 0) for h in hours]

    fig, (ax1, ax2, ax3) = plt.subplots(3, 1, figsize=(14, 14))

    # 1. 시간대별 실제 사용 시간 (분)
    ax1.bar(hours, usage_minutes, color='green', alpha=0.7)
    ax1.set_xlabel('시간 (Hour)', fontsize=12)
    ax1.set_ylabel('사용 시간 (분)', fontsize=12)
    ax1.set_title(f'{date_str} - 시간대별 실제 사용 시간', fontsize=14, fontweight='bold')
    ax1.set_xticks(hours)
    ax1.grid(axis='y', alpha=0.3)

    # 사용 시간 표시
    for i, v in enumerate(usage_minutes):
        if v > 0:
            ax1.text(i, v, f'{v:.1f}', ha='center', va='bottom', fontsize=9)

    # 2. 총 이벤트 수
    ax2.bar(hours, events, color='skyblue', alpha=0.7)
    ax2.set_xlabel('시간 (Hour)', fontsize=12)
    ax2.set_ylabel('이벤트 수', fontsize=12)
    ax2.set_title(f'{date_str} - 시간대별 총 앱 이벤트 수', fontsize=14, fontweight='bold')
    ax2.set_xticks(hours)
    ax2.grid(axis='y', alpha=0.3)

    # 이벤트 수 표시
    for i, v in enumerate(events):
        if v > 0:
            ax2.text(i, v, str(v), ha='center', va='bottom', fontsize=9)

    # 3. 포그라운드 전환 수
    ax3.bar(hours, foregrounds, color='coral', alpha=0.7)
    ax3.set_xlabel('시간 (Hour)', fontsize=12)
    ax3.set_ylabel('앱 전환 횟수', fontsize=12)
    ax3.set_title(f'{date_str} - 시간대별 앱 전환 횟수 (포그라운드)', fontsize=14, fontweight='bold')
    ax3.set_xticks(hours)
    ax3.grid(axis='y', alpha=0.3)

    # 전환 횟수 표시
    for i, v in enumerate(foregrounds):
        if v > 0:
            ax3.text(i, v, str(v), ha='center', va='bottom', fontsize=9)

    plt.tight_layout()
    return fig


def plot_top_apps_by_hour(hourly_app_usage, date_str, top_n=10):
    """시간대별 상위 앱 사용 패턴 (실제 사용 시간 기준)"""

    # 전체 기간에서 가장 많이 사용된 앱 찾기 (분 단위)
    total_app_usage = Counter()
    for hour_data in hourly_app_usage.values():
        total_app_usage.update(hour_data)

    top_apps = [app for app, _ in total_app_usage.most_common(top_n)]

    # 시간대별 상위 앱 사용량 매트릭스 (분)
    hours = range(24)
    usage_matrix = []

    for app in top_apps:
        app_hourly = [hourly_app_usage[h].get(app, 0) for h in hours]
        usage_matrix.append(app_hourly)

    # 히트맵 스타일 그래프
    fig, ax = plt.subplots(figsize=(16, 8))

    # 각 앱별로 라인 플롯
    for i, app in enumerate(top_apps):
        ax.plot(hours, usage_matrix[i], marker='o', label=app, linewidth=2, markersize=6)

    ax.set_xlabel('시간 (Hour)', fontsize=12)
    ax.set_ylabel('사용 시간 (분)', fontsize=12)
    ax.set_title(f'{date_str} - 시간대별 상위 {top_n}개 앱 사용 시간', fontsize=14, fontweight='bold')
    ax.set_xticks(hours)
    ax.grid(True, alpha=0.3)
    ax.legend(bbox_to_anchor=(1.05, 1), loc='upper left', fontsize=10)

    plt.tight_layout()
    return fig


def plot_hourly_heatmap(hourly_app_usage, date_str, top_n=15):
    """시간대별 앱 사용 히트맵 (실제 사용 시간 기준)"""
    import numpy as np

    # 전체 기간에서 가장 많이 사용된 앱 찾기 (분 단위)
    total_app_usage = Counter()
    for hour_data in hourly_app_usage.values():
        total_app_usage.update(hour_data)

    top_apps = [app for app, _ in total_app_usage.most_common(top_n)]

    # 매트릭스 생성
    hours = range(24)
    matrix = np.zeros((len(top_apps), 24))

    for i, app in enumerate(top_apps):
        for h in hours:
            matrix[i][h] = hourly_app_usage[h].get(app, 0)

    fig, ax = plt.subplots(figsize=(16, 10))
    im = ax.imshow(matrix, cmap='YlOrRd', aspect='auto')

    # 축 레이블 설정
    ax.set_xticks(range(24))
    ax.set_yticks(range(len(top_apps)))
    ax.set_xticklabels(hours)
    ax.set_yticklabels(top_apps)

    ax.set_xlabel('시간 (Hour)', fontsize=12)
    ax.set_ylabel('앱 이름', fontsize=12)
    ax.set_title(f'{date_str} - 시간대별 앱 사용 히트맵 (상위 {top_n}개, 단위: 분)', fontsize=14, fontweight='bold')

    # 컬러바 추가
    cbar = plt.colorbar(im, ax=ax)
    cbar.set_label('사용 시간 (분)', fontsize=11)

    # 각 셀에 값 표시
    for i in range(len(top_apps)):
        for j in range(24):
            value = matrix[i][j]
            if value > 0:
                text = ax.text(j, i, f'{value:.1f}',
                             ha="center", va="center",
                             color="black" if value < matrix.max()/2 else "white",
                             fontsize=8)

    plt.tight_layout()
    return fig


def generate_summary_stats(hourly_events, hourly_foreground, hourly_usage_minutes, hourly_app_usage, data):
    """요약 통계 생성"""
    total_events = sum(hourly_events.values())
    total_foreground = sum(hourly_foreground.values())
    total_usage_minutes = sum(hourly_usage_minutes.values())

    # 가장 활동적인 시간 (사용 시간 기준)
    if hourly_usage_minutes:
        peak_hour, peak_minutes = max(hourly_usage_minutes.items(), key=lambda x: x[1])
    else:
        peak_hour, peak_minutes = 0, 0

    # 전체 기간 상위 앱 (사용 시간 기준)
    total_app_usage = Counter()
    for hour_data in hourly_app_usage.values():
        total_app_usage.update(hour_data)

    top_apps = total_app_usage.most_common(5)

    # 고유 앱 수
    unique_apps = set(item['name'] for item in data)

    summary = {
        '총 사용 시간': f"{total_usage_minutes:.1f}분 ({total_usage_minutes/60:.1f}시간)",
        '총 이벤트 수': total_events,
        '총 앱 전환 횟수': total_foreground,
        '활동 시간대 수': len([h for h in hourly_usage_minutes if hourly_usage_minutes[h] > 0]),
        '가장 활동적인 시간': f"{peak_hour}시 ({peak_minutes:.1f}분)",
        '사용한 고유 앱 수': len(unique_apps),
        '상위 5개 앱 (사용 시간)': [(name, f'{minutes:.1f}분') for name, minutes in top_apps]
    }

    return summary


def visualize_daily_usage(json_file_path, output_dir=None):
    """일일 사용량 시각화 메인 함수"""

    # 데이터 로드
    print(f"📂 Loading {json_file_path}...")
    data = load_daily_data(json_file_path)
    print(f"✅ Loaded {len(data):,} records")

    # 날짜 추출
    file_path = Path(json_file_path)
    date_str = file_path.stem  # 파일명에서 날짜 추출 (예: 2025-10-25)

    # 분석
    print("📊 Analyzing hourly usage...")
    hourly_events, hourly_foreground, hourly_usage_minutes, hourly_app_usage = analyze_hourly_usage(data)

    # 요약 통계
    summary = generate_summary_stats(hourly_events, hourly_foreground, hourly_usage_minutes, hourly_app_usage, data)

    print("\n" + "="*60)
    print(f"📈 {date_str} 사용량 요약")
    print("="*60)
    for key, value in summary.items():
        if key == '상위 5개 앱 (사용 시간)':
            print(f"\n{key}:")
            for i, (app_name, time_str) in enumerate(value, 1):
                print(f"  {i}. {app_name}: {time_str}")
        else:
            print(f"{key}: {value}")
    print("="*60 + "\n")

    # 출력 디렉토리 설정
    if output_dir is None:
        output_dir = file_path.parent / "visualizations"
    else:
        output_dir = Path(output_dir)

    output_dir.mkdir(exist_ok=True)

    # 그래프 생성 및 저장
    print("📊 Generating visualizations...")

    # 1. 시간대별 활동량
    print("  1/3 시간대별 활동량...")
    fig1 = plot_hourly_activity(hourly_events, hourly_foreground, hourly_usage_minutes, date_str)
    output_file1 = output_dir / f"{date_str}_hourly_activity.png"
    fig1.savefig(output_file1, dpi=150, bbox_inches='tight')
    plt.close(fig1)
    print(f"     ✅ Saved: {output_file1}")

    # 2. 상위 앱 사용 패턴
    print("  2/3 상위 앱 사용 패턴...")
    fig2 = plot_top_apps_by_hour(hourly_app_usage, date_str, top_n=10)
    output_file2 = output_dir / f"{date_str}_top_apps_pattern.png"
    fig2.savefig(output_file2, dpi=150, bbox_inches='tight')
    plt.close(fig2)
    print(f"     ✅ Saved: {output_file2}")

    # 3. 히트맵
    print("  3/3 시간대별 히트맵...")
    fig3 = plot_hourly_heatmap(hourly_app_usage, date_str, top_n=15)
    output_file3 = output_dir / f"{date_str}_heatmap.png"
    fig3.savefig(output_file3, dpi=150, bbox_inches='tight')
    plt.close(fig3)
    print(f"     ✅ Saved: {output_file3}")

    print(f"\n✅ All visualizations saved to: {output_dir}/")
    print(f"   - {output_file1.name}")
    print(f"   - {output_file2.name}")
    print(f"   - {output_file3.name}")


def main():
    parser = argparse.ArgumentParser(description='하루치 JSON의 시간대별 사용량을 시각화합니다.')
    parser.add_argument('json_file', help='날짜별 JSON 파일 (예: daily_{user_id}_2025/2025-10-26.json)')
    parser.add_argument('--output-dir', help='출력 디렉토리 (기본값: 입력 파일 옆 visualizations)')
    args = parser.parse_args()

    visualize_daily_usage(args.json_file, output_dir=args.output_dir)


if __name__ == '__main__':
    main()
