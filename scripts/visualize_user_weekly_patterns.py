#!/usr/bin/env python3
"""
사용자별 요일별 사용 시간 및 앱 다양성 시각화 스크립트
"""

import json
from datetime import datetime
from collections import defaultdict
from pathlib import Path
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
import numpy as np

# 한글 폰트 설정
plt.rcParams['font.family'] = 'AppleGothic'  # macOS
plt.rcParams['axes.unicode_minus'] = False  # 마이너스 기호 깨짐 방지

# 요일 한글 이름
WEEKDAY_NAMES = ['월요일', '화요일', '수요일', '목요일', '금요일', '토요일', '일요일']


def load_user_data(json_file_path):
    """사용자 JSON 파일 로드"""
    with open(json_file_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def calculate_usage_time(events):
    """이벤트 데이터로부터 실제 사용 시간 계산"""
    # 데이터를 timestamp로 정렬
    sorted_events = sorted(events, key=lambda x: x['timestamp'])

    # 앱별 활성 세션 추적
    active_sessions = {}
    total_usage_ms = 0

    for event in sorted_events:
        timestamp_ms = event['timestamp']
        package_name = event.get('package_name') or event.get('packageName')
        event_type = event['type']

        if event_type == 'MOVE_TO_FOREGROUND':
            # 기존 세션이 있으면 종료 처리
            if package_name in active_sessions:
                start_time = active_sessions[package_name]
                usage_ms = timestamp_ms - start_time
                total_usage_ms += usage_ms

            # 새 세션 시작
            active_sessions[package_name] = timestamp_ms

        elif event_type == 'MOVE_TO_BACKGROUND':
            # 세션 종료 및 사용 시간 계산
            if package_name in active_sessions:
                start_time = active_sessions[package_name]
                usage_ms = timestamp_ms - start_time
                total_usage_ms += usage_ms
                del active_sessions[package_name]

    # 아직 종료되지 않은 세션 처리
    if sorted_events and active_sessions:
        last_timestamp = sorted_events[-1]['timestamp']
        for package_name, start_time in active_sessions.items():
            usage_ms = last_timestamp - start_time
            total_usage_ms += usage_ms

    return total_usage_ms / 60000.0  # 분 단위로 반환


def analyze_weekly_patterns(data):
    """요일별 사용 패턴 분석"""
    # 요일별 데이터 수집
    weekday_events = defaultdict(list)
    weekday_apps = defaultdict(set)

    for event in data:
        timestamp_ms = event['timestamp']
        dt = datetime.fromtimestamp(timestamp_ms / 1000.0)
        weekday = dt.weekday()  # 0=월요일, 6=일요일

        weekday_events[weekday].append(event)

        # 앱 이름 추출
        app_name = event.get('name')
        if app_name:
            weekday_apps[weekday].add(app_name)

    # 요일별 사용 시간 계산
    weekday_usage_minutes = {}
    for weekday, events in weekday_events.items():
        weekday_usage_minutes[weekday] = calculate_usage_time(events)

    # 요일별 고유 앱 수
    weekday_app_count = {weekday: len(apps) for weekday, apps in weekday_apps.items()}

    return weekday_usage_minutes, weekday_app_count


def plot_user_weekly_usage(user_id, weekday_usage_minutes, weekday_app_count):
    """사용자의 요일별 사용 패턴 시각화"""
    weekdays = range(7)
    usage_minutes = [weekday_usage_minutes.get(wd, 0) for wd in weekdays]
    app_counts = [weekday_app_count.get(wd, 0) for wd in weekdays]
    usage_hours = [m / 60.0 for m in usage_minutes]

    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10))

    # 1. 요일별 사용 시간
    bars1 = ax1.bar(weekdays, usage_hours, color='steelblue', alpha=0.7, edgecolor='navy')
    ax1.set_xlabel('요일', fontsize=12)
    ax1.set_ylabel('사용 시간 (시간)', fontsize=12)
    ax1.set_title(f'사용자 {user_id[:8]}... - 요일별 총 사용 시간', fontsize=14, fontweight='bold')
    ax1.set_xticks(weekdays)
    ax1.set_xticklabels(WEEKDAY_NAMES)
    ax1.grid(axis='y', alpha=0.3)

    # 값 표시
    for i, (bar, hours, minutes) in enumerate(zip(bars1, usage_hours, usage_minutes)):
        if hours > 0:
            ax1.text(bar.get_x() + bar.get_width()/2, hours,
                    f'{hours:.1f}h\n({minutes:.0f}m)',
                    ha='center', va='bottom', fontsize=9)

    # 2. 요일별 앱 다양성
    bars2 = ax2.bar(weekdays, app_counts, color='coral', alpha=0.7, edgecolor='darkred')
    ax2.set_xlabel('요일', fontsize=12)
    ax2.set_ylabel('고유 앱 수', fontsize=12)
    ax2.set_title(f'사용자 {user_id[:8]}... - 요일별 사용 앱 다양성', fontsize=14, fontweight='bold')
    ax2.set_xticks(weekdays)
    ax2.set_xticklabels(WEEKDAY_NAMES)
    ax2.grid(axis='y', alpha=0.3)

    # 값 표시
    for bar, count in zip(bars2, app_counts):
        if count > 0:
            ax2.text(bar.get_x() + bar.get_width()/2, count,
                    str(count), ha='center', va='bottom', fontsize=10)

    plt.tight_layout()
    return fig


def plot_all_users_comparison(users_data, metric='usage'):
    """여러 사용자의 요일별 패턴 비교"""
    fig, ax = plt.subplots(figsize=(16, 8))
    weekdays = range(7)

    for user_id, (weekday_usage, weekday_apps) in users_data.items():
        if metric == 'usage':
            values = [weekday_usage.get(wd, 0) / 60.0 for wd in weekdays]  # 시간 단위
            ylabel = '사용 시간 (시간)'
            title = '사용자별 요일별 사용 시간 비교'
        else:  # diversity
            values = [weekday_apps.get(wd, 0) for wd in weekdays]
            ylabel = '고유 앱 수'
            title = '사용자별 요일별 앱 다양성 비교'

        ax.plot(weekdays, values, marker='o', label=f'User {user_id[:8]}...',
               linewidth=2, markersize=6, alpha=0.7)

    ax.set_xlabel('요일', fontsize=12)
    ax.set_ylabel(ylabel, fontsize=12)
    ax.set_title(title, fontsize=14, fontweight='bold')
    ax.set_xticks(weekdays)
    ax.set_xticklabels(WEEKDAY_NAMES)
    ax.grid(True, alpha=0.3)
    ax.legend(bbox_to_anchor=(1.05, 1), loc='upper left', fontsize=9)

    plt.tight_layout()
    return fig


def plot_heatmap(users_data, metric='usage'):
    """사용자-요일 히트맵"""
    user_ids = list(users_data.keys())
    weekdays = range(7)

    # 매트릭스 생성
    matrix = np.zeros((len(user_ids), 7))

    for i, user_id in enumerate(user_ids):
        weekday_usage, weekday_apps = users_data[user_id]
        for wd in weekdays:
            if metric == 'usage':
                matrix[i][wd] = weekday_usage.get(wd, 0) / 60.0  # 시간 단위
            else:  # diversity
                matrix[i][wd] = weekday_apps.get(wd, 0)

    fig, ax = plt.subplots(figsize=(12, max(8, len(user_ids) * 0.4)))

    im = ax.imshow(matrix, cmap='YlOrRd', aspect='auto')

    # 축 레이블 설정
    ax.set_xticks(weekdays)
    ax.set_yticks(range(len(user_ids)))
    ax.set_xticklabels(WEEKDAY_NAMES)
    ax.set_yticklabels([f'User {uid[:8]}...' for uid in user_ids], fontsize=8)

    ax.set_xlabel('요일', fontsize=12)
    ax.set_ylabel('사용자', fontsize=12)

    if metric == 'usage':
        title = '사용자-요일별 사용 시간 히트맵 (시간)'
        label = '사용 시간 (시간)'
    else:
        title = '사용자-요일별 앱 다양성 히트맵'
        label = '고유 앱 수'

    ax.set_title(title, fontsize=14, fontweight='bold')

    # 컬러바 추가
    cbar = plt.colorbar(im, ax=ax)
    cbar.set_label(label, fontsize=11)

    # 각 셀에 값 표시
    for i in range(len(user_ids)):
        for j in range(7):
            value = matrix[i][j]
            if value > 0:
                text = ax.text(j, i, f'{value:.1f}' if metric == 'usage' else f'{int(value)}',
                             ha="center", va="center",
                             color="black" if value < matrix.max()/2 else "white",
                             fontsize=7)

    plt.tight_layout()
    return fig


def visualize_users_weekly_patterns(users_dir, output_dir=None, max_users=None):
    """메인 시각화 함수"""
    users_dir = Path(users_dir)

    # 사용자 파일 목록
    user_files = sorted([f for f in users_dir.glob('user_*.json')])

    if max_users:
        user_files = user_files[:max_users]

    print(f"📂 Found {len(user_files)} user files")

    # 출력 디렉토리 설정
    if output_dir is None:
        output_dir = users_dir.parent / "weekly_visualizations"
    else:
        output_dir = Path(output_dir)

    output_dir.mkdir(exist_ok=True)

    # 사용자별 데이터 분석
    users_data = {}

    for i, user_file in enumerate(user_files, 1):
        user_id = user_file.stem.replace('user_', '')
        print(f"  [{i}/{len(user_files)}] Analyzing {user_id[:16]}...")

        try:
            data = load_user_data(user_file)
            weekday_usage, weekday_apps = analyze_weekly_patterns(data)
            users_data[user_id] = (weekday_usage, weekday_apps)

            # 개별 사용자 그래프 생성
            fig = plot_user_weekly_usage(user_id, weekday_usage, weekday_apps)
            output_file = output_dir / f"user_{user_id}_weekly.png"
            fig.savefig(output_file, dpi=150, bbox_inches='tight')
            plt.close(fig)

        except Exception as e:
            print(f"    ⚠️  Error processing {user_id}: {e}")
            continue

    print(f"\n✅ Analyzed {len(users_data)} users successfully")

    # 전체 사용자 비교 그래프
    if len(users_data) > 1:
        print("\n📊 Generating comparison visualizations...")

        # 1. 사용 시간 비교
        print("  1/4 사용 시간 비교 그래프...")
        fig_usage = plot_all_users_comparison(users_data, metric='usage')
        output_usage = output_dir / "all_users_usage_comparison.png"
        fig_usage.savefig(output_usage, dpi=150, bbox_inches='tight')
        plt.close(fig_usage)

        # 2. 앱 다양성 비교
        print("  2/4 앱 다양성 비교 그래프...")
        fig_diversity = plot_all_users_comparison(users_data, metric='diversity')
        output_diversity = output_dir / "all_users_diversity_comparison.png"
        fig_diversity.savefig(output_diversity, dpi=150, bbox_inches='tight')
        plt.close(fig_diversity)

        # 3. 사용 시간 히트맵
        print("  3/4 사용 시간 히트맵...")
        fig_heatmap_usage = plot_heatmap(users_data, metric='usage')
        output_heatmap_usage = output_dir / "all_users_usage_heatmap.png"
        fig_heatmap_usage.savefig(output_heatmap_usage, dpi=150, bbox_inches='tight')
        plt.close(fig_heatmap_usage)

        # 4. 앱 다양성 히트맵
        print("  4/4 앱 다양성 히트맵...")
        fig_heatmap_diversity = plot_heatmap(users_data, metric='diversity')
        output_heatmap_diversity = output_dir / "all_users_diversity_heatmap.png"
        fig_heatmap_diversity.savefig(output_heatmap_diversity, dpi=150, bbox_inches='tight')
        plt.close(fig_heatmap_diversity)

    # 통계 출력
    print("\n" + "="*60)
    print("📈 요약 통계")
    print("="*60)

    for user_id, (weekday_usage, weekday_apps) in users_data.items():
        total_usage = sum(weekday_usage.values()) / 60.0  # 시간
        avg_apps = sum(weekday_apps.values()) / len(weekday_apps) if weekday_apps else 0
        print(f"\nUser {user_id[:16]}...")
        print(f"  총 사용 시간: {total_usage:.1f}시간")
        print(f"  평균 일일 앱 다양성: {avg_apps:.1f}개")

        # 가장 활발한 요일
        if weekday_usage:
            max_weekday = max(weekday_usage.items(), key=lambda x: x[1])
            print(f"  가장 활발한 요일: {WEEKDAY_NAMES[max_weekday[0]]} ({max_weekday[1]/60:.1f}시간)")

    print("="*60)
    print(f"\n✅ All visualizations saved to: {output_dir}/")
    print(f"   - {len(users_data)} individual user graphs")
    if len(users_data) > 1:
        print(f"   - 4 comparison graphs")


if __name__ == '__main__':
    # 사용 예시
    users_dir = "/Users/layla/IdeaProjects/backend/scripts/users"

    # 모든 사용자 분석 (또는 max_users=5 등으로 제한 가능)
    visualize_users_weekly_patterns(users_dir, max_users=10)

    # 특정 사용자만 분석하려면:
    # visualize_users_weekly_patterns(users_dir, max_users=5)
