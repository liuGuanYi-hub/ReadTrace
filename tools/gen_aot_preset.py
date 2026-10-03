# -*- coding: utf-8 -*-
"""生成《进击的巨人》六季的 preset 条目并合并进 preset_all.json。

数据来源：
- 机器字段（标题/评分/标签/简介/导演/制作/话数）取自 Bangumi API 实测数据（build/bgm/）
- 内容字段（金句/短评/章节大纲/语录/六维心智/角色身份）为人工撰写
"""
import collections
import glob
import json
import os
import re
from datetime import datetime, timezone, timedelta

BUILD = 'build/bgm'
PRESET = 'app/src/main/assets/preset_all.json'
CST = timezone(timedelta(hours=8))

SEASON_ORDER = [55770, 118335, 217300, 263750, 285666, 331752]

# ---------------------------------------------------------------- 人工内容
CONTENT = {
    55770: dict(
        title='进击的巨人', category='热血战斗',
        short_comment='那一天，人类回想起了被巨人支配的恐惧，以及被囚禁于鸟笼中的屈辱。',
        review='用最原始的恐惧和最决绝的反抗，把「自由」两个字刻进了每一帧。它不是爽番，是一把钝刀，慢慢割开你对安全感的所有幻想。',
        outline_titles=['城墙崩塌之日', '训练兵团的淬炼', '托洛斯特区攻防', '女巨人的阴影', '壁外调查与裂痕'],
        outlines=[
            '超大型巨人踏破希干希纳区外墙，玛利亚之墙陷落；艾伦亲眼看着母亲被巨人吞食，立誓驱逐一切巨人。',
            '104 期训练兵团。从「想逃回家」的懦弱少年，到在立体机动装置上找到自己唯一的意义。',
            '托洛斯特区补给站攻防战。艾伦巨人化的能力在生死一线初次觉醒，也第一次被人类当作怪物。',
            '女巨人突袭壁外调查，调查兵团遭遇史上最惨重伤亡。阿尔敏第一次用「她只是想确认艾伦的确切位置」推导出同伴的身份。',
            '墙外真相的第一道裂缝：巨人从何而来、墙里到底藏了什么、政府为什么要封锁一切。',
        ],
        notes=[
            '战斗，战斗，直到把每一个敌人都杀光。',
            '这个世界是残酷的，但同时也是美丽的。',
            '如果不确定能赢就不战斗，那我们就什么都赢不了。',
        ],
        chars=['エレン・イェーガー', 'ミカサ・アッカーマン', 'アルミン・アルレルト', 'リヴァイ',
               'エルヴィン・スミス', 'ハンジ・ゾエ', 'ジャン・キルシュタイン', 'サシャ・ブラウス',
               'コニー・スプリンガー', 'アニ・レオンハート'],
        mindprint=dict(depthScore=9.2, artistryScore=9.0, emotionScore=9.4,
                       logicScore=8.6, difficultyScore=6.8, healingScore=2.6),
    ),
    118335: dict(
        title='进击的巨人 第二季', category='热血战斗',
        short_comment='有时候，真相比恐惧更让人窒息。',
        review='把「敌人是谁」这个问题的答案，从墙外一步步拖进了最亲近的人身上。第二季最残忍的地方在于——它让你看懂了莱纳脸上的那个表情。',
        outline_titles=['兽之巨人', '尤米尔与希斯特里亚', '莱纳与贝特霍尔德的自白', '夺回作战', '坐标之力'],
        outlines=[
            '会说话的巨人出现。兽之巨人指挥无垢巨人发动夜袭，调查兵团再次被从内部撕开。',
            '尤米尔回忆起自己以「尤米尔之民」身份活过的六十年。希斯特里亚在生死之际拒绝「成为好孩子」。',
            '铠甲与超大型巨人的真身揭晓。他们坐在一起，平静地告诉艾伦：「我们的使命，是把你们全部杀光。」',
            '联合部队对铠之巨人发动夺还作战，艾伦被夺走。阿尔敏第一次以「赌上性命」的形态说服众人。',
            '艾伦发动「坐标」之力，无垢巨人开始听从他的号令——巨人之力背后的那份「联结」第一次显形。',
        ],
        notes=[
            '我是铠之巨人，他是超大型巨人。',
            '我们只是想回家而已。',
            '不要因为看不见希望就放弃决断。',
        ],
        chars=['エレン・イェーガー', 'ミカサ・アッカーマン', 'アルミン・アルレルト', 'リヴァイ',
               'エルヴィン・スミス', 'ハンジ・ゾエ', 'ライナー・ブラウン', 'ベルトルト・フーバー',
               'ユミル', 'クリスタ・レンズ', 'ジーク・イェーガー'],
        mindprint=dict(depthScore=9.4, artistryScore=9.0, emotionScore=9.5,
                       logicScore=8.8, difficultyScore=7.0, healingScore=2.4),
    ),
    217300: dict(
        title='进击的巨人 第三季', category='权谋反转',
        short_comment='敌人不在墙外，而在王座之上。',
        review='把战场从荒原搬进了王都。当对手从巨人变成人类、从怪物变成同胞，这部作品真正开始谈政治——也是最容易被低估的一季。',
        outline_titles=['王政府的暗影', '中央宪兵与人际杀戮', '雷斯家族的真相', '王都政变', '加冕'],
        outlines=[
            '调查兵团被通缉。利威尔班带着希斯特里亚藏进森林，躲避中央宪兵的猎杀。',
            '首次大规模的人对人立体机动战。对手不是巨人，是拿着刀子的同类——这一刻作品的底色彻底变了。',
            '雷斯家族的真相浮出水面：巨人之力的起源、「不战之誓」，以及墙内世界被编织的世界观。',
            '推翻伪王、夺取王都。埃尔文以整支兵团的信誉押上一场必输的赌局。',
            '希斯特里亚加冕。她拒绝成为任何人手里的「好孩子」，第一次以女王的身份选择了自己的立场。',
        ],
        notes=[
            '王政是必要的，但前提是它得先保护人民。',
            '我们不是在和巨人战斗，我们是在和看不见的敌人战斗。',
            '把自己交给什么？连自己要什么都不知道的人，谈何献出心脏。',
        ],
        chars=['エレン・イェーガー', 'ミカサ・アッカーマン', 'アルミン・アルレルト', 'リヴァイ',
               'エルヴィン・スミス', 'ハンジ・ゾエ', 'クリスタ・レンズ', 'ジャン・キルシュタイン',
               'ロッド・レイス', 'ケニー・アッカーマン'],
        mindprint=dict(depthScore=9.5, artistryScore=9.1, emotionScore=9.1,
                       logicScore=9.2, difficultyScore=7.2, healingScore=2.8),
    ),
    263750: dict(
        title='进击的巨人 第三季 Part.2', category='史诗悲剧',
        short_comment='海的那边，是自由，还是另一个更大的牢笼？',
        review='全作公认的情感最高点。埃尔文的冲锋与阿尔敏的选择，把「献出心脏」这句话从口号变成了必须支付的对价——看完那集，你会很久说不出话。',
        outline_titles=['玛利亚之墙夺回战', '阿尔敏的决断', '利威尔对兽之巨人', '埃尔文的冲锋', '看海'],
        outlines=[
            '兽之巨人用投石撕开调查兵团的防线。以少对多的绝境，第一次让「胜利」变成一个需要精算的赌局。',
            '阿尔敏以身为饵迎战超大型巨人，用烧掉自己的方式为艾伦换来唯一的破绽。两个人在屋顶上的选择，是全作最锋利的一刀。',
            '利威尔以重伤之身追上兽之巨人。人类最强士兵的复仇，代价却是不得不做的那次交换。',
            '埃尔文向新兵喊出「献出你们的心脏」，带领他们冲向死亡的正面。他没能抵达地下室——这个结局本身就是答案。',
            '地下室、格里沙的记忆、墙外的世界。艾伦站在海边，说出那句：「海的那边是敌人。如果我们把敌人全杀光，我们就能获得自由吗？」',
        ],
        notes=[
            '献出你们的心脏。',
            '我从来没有想过要赢，我只是想做出一个不让自己后悔的决断。',
            '海的那边是敌人。可是如果我们把敌人全杀光，我们就真的自由了吗？',
        ],
        chars=['エレン・イェーガー', 'ミカサ・アッカーマン', 'アルミン・アルレルト', 'リヴァイ',
               'エルヴィン・スミス', 'ハンジ・ゾエ', 'ジャン・キルシュタイン', 'サシャ・ブラウス',
               'コニー・スプリンガー', 'ジーク・イェーガー'],
        mindprint=dict(depthScore=9.6, artistryScore=9.3, emotionScore=9.6,
                       logicScore=9.0, difficultyScore=7.5, healingScore=2.2),
    ),
    285666: dict(
        title='进击的巨人 最终季', category='史诗悲剧',
        short_comment='从这一季开始，你不再知道该为谁流泪。',
        review='视角一翻转，整部作品就重写了一遍。这一季最狠的地方不是战斗，是让你意识到——前三季里你为之心碎的每一个人，在另一群人眼里都是灾难。',
        outline_titles=['马莱的战场', '戴巴家族的宣战', '雷贝里欧之战', '耶格尔派的崛起', '地鸣前夜'],
        outlines=[
            '视角切换到海对岸。法尔科、贾碧、收容区的日常——马莱的孩子们也在为生存挣扎。',
            '威利·戴巴在雷贝里欧的舞台上宣告：「我向帕拉迪岛的恶魔宣战。」话音未落，艾伦从地下室走出。',
            '帕拉迪岛与马莱的首次正面碰撞。艾伦撕开战场，而调查兵团不得不与曾经的敌人并肩。',
            '岛内的裂痕。耶格尔派以「真正的自由」之名夺取兵团控制权，帕拉迪岛开始从内部瓦解。',
            '艾伦与吉克的同盟、路基的抉择、地鸣的启动条件。一切都指向那句台词：「我将毁灭世界。」',
        ],
        notes=[
            '我向帕拉迪岛的恶魔宣战。',
            '如果谁都不愿意流血，那就什么都改变不了。',
            '我不是主角，我只是一个想把家乡夺回来的人。',
        ],
        chars=['エレン・イェーガー', 'ミカサ・アッカーマン', 'アルミン・アルレルト', 'リヴァイ',
               'ハンジ・ゾエ', 'ジャン・キルシュタイン', 'ガビ・ブラウン', 'ファルコ・グライス',
               'ジーク・イェーガー', 'ライナー・ブラウン', 'ウィリー・タイバー'],
        mindprint=dict(depthScore=9.7, artistryScore=9.2, emotionScore=9.4,
                       logicScore=9.3, difficultyScore=7.8, healingScore=1.8),
    ),
    331752: dict(
        title='进击的巨人 最终季 Part.2', category='史诗悲剧',
        short_comment='每个人都是自己故事里的英雄，也是别人故事里的恶人。',
        review='地鸣踏过大陆的那一刻，这部作品完成了它从第一集就埋下的命题：自由从来不是被给予的，而人为了它愿意付的代价，恰好就是它的重量。',
        outline_titles=['地鸣启动', '分歧的同盟', '尤米尔的两千年', '空中的决战', '自由的意义'],
        outlines=[
            '地鸣启动。数千万超大型巨人踏平马莱与整个世界——这不是胜利，是一场以「自由」为名的献祭。',
            '曾经的死敌聚在同一张桌上。莱纳、阿尼、皮克与调查兵团残余结成同盟，只为阻止一个人。',
            '尤米尔两千年的等待被揭开：她一直在等一个人，能说出那句「你不是奴隶，你有选择的自由」。',
            '阿明与艾伦在空中对撞。两个一起长大的少年，第一次也是最后一次以完全不同的人的身份交手。',
            '结局。艾伦的选择、阿尔敏得到的答案、以及那句贯穿全作的追问——把敌人全部杀光之后，我们真的自由了吗？',
        ],
        notes=[
            '你不是奴隶，你有选择的自由。',
            '米卡萨，谢谢你给我围上这条围巾。',
            '也许我们从一开始就搞错了自由的意思。',
        ],
        chars=['エレン・イェーガー', 'ミカサ・アッカーマン', 'アルミン・アルレルト', 'リヴァイ',
               'ハンジ・ゾエ', 'ジャン・キルシュタイン', 'ガビ・ブラウン', 'ファルコ・グライス',
               'ジーク・イェーガー', 'ライナー・ブラウン', 'アニ・レオンハート', 'ピーク・フィンガー'],
        mindprint=dict(depthScore=9.8, artistryScore=9.3, emotionScore=9.7,
                       logicScore=9.1, difficultyScore=8.0, healingScore=1.5),
    ),
}

# 角色：日文名 -> (中文名, 身份, emoji)
CHARACTERS = {
    'エレン・イェーガー': ('艾伦·耶格尔', '调查兵团 · 进击的巨人', '⚔️'),
    'ミカサ・アッカーマン': ('三笠·阿克曼', '调查兵团 · 人类最强候补', '🧣'),
    'アルミン・アルレルト': ('阿尔敏·阿诺德', '调查兵团 · 战术参谋', '📖'),
    'リヴァイ': ('利威尔', '调查兵团 · 人类最强士兵', '🗡️'),
    'エルヴィン・スミス': ('埃尔文·史密斯', '调查兵团第13代团长', '🦅'),
    'ハンジ・ゾエ': ('韩吉·佐耶', '调查兵团 · 巨人研究者', '🔬'),
    'ジャン・キルシュタイン': ('让·基尔希斯坦', '调查兵团 · 现实主义指挥', '🐴'),
    'サシャ・ブラウス': ('萨莎·布劳斯', '调查兵团 · 猎人之女', '🏹'),
    'コニー・スプリンガー': ('康尼·斯普林格', '调查兵团 · 出身托洛斯特区', '🪖'),
    'アニ・レオンハート': ('阿尼·利昂纳德', '女巨人 · 宪兵团前成员', '💠'),
    'ライナー・ブラウン': ('莱纳·布朗', '铠之巨人 · 马莱战士', '🛡️'),
    'ベルトルト・フーバー': ('贝特霍尔德·胡佛', '超大型巨人 · 马莱战士', '🔥'),
    'ユミル': ('尤米尔', '颚之巨人前身 · 尤米尔之民', '🗝️'),
    'クリスタ・レンズ': ('希斯特里亚·雷斯', '雷斯家正统 · 现任女王', '👑'),
    'ジーク・イェーガー': ('吉克·耶格尔', '兽之巨人 · 马莱战士长', '🐒'),
    'ロッド・レイス': ('罗德·雷斯', '雷斯家当主 · 伪王幕后', '⛓️'),
    'ケニー・アッカーマン': ('凯尼·阿克曼', '中央宪兵 · 阿克曼一族', '🔪'),
    'ガビ・ブラウン': ('贾碧·布朗', '马莱战士候补生', '🎯'),
    'ファルコ・グライス': ('法尔科·格莱斯', '马莱战士候补生', '🕊️'),
    'ウィリー・タイバー': ('威利·戴巴', '戴巴家当主 · 马莱实际掌权者', '🎭'),
    'ピーク・フィンガー': ('皮克·芬格尔', '车力巨人 · 马莱战士', '🐎'),
}


def clean(text):
    if not text:
        return ''
    return re.sub(r'\s+', ' ', text.replace('\r', ' ').replace('\n', ' ')).strip()


def main():
    subjects = {}
    for sid in SEASON_ORDER:
        with open(f'{BUILD}/subject_{sid}.json', encoding='utf-8') as f:
            subjects[sid] = json.load(f)

    chars_raw = {}
    for sid in SEASON_ORDER:
        p = f'{BUILD}/chars_{sid}.json'
        if os.path.exists(p):
            with open(p, encoding='utf-8') as f:
                chars_raw[sid] = json.load(f)

    with open(PRESET, encoding='utf-8') as f:
        preset = json.load(f, object_pairs_hook=collections.OrderedDict)

    works = preset['works']
    existing_titles = {w.get('title') for w in works}
    now = datetime.now(CST).isoformat()

    added, skipped = [], []

    for sid in SEASON_ORDER:
        sub = subjects[sid]
        cfg = CONTENT[sid]
        title = cfg['title']

        if title in existing_titles:
            skipped.append(title)
            continue

        # --- 导演 / 制作公司 ---
        director = studio = ''
        for box in (sub.get('infobox') or []):
            if box.get('key') == '导演':
                v = box.get('value')
                director = clean(v if isinstance(v, str) else (v[0].get('v', '') if v else ''))
            if box.get('key') in ('动画制作', '制作'):
                v = box.get('value')
                studio = clean(v if isinstance(v, str) else (v[0].get('v', '') if v else ''))
        if not studio:
            tags = [t.get('name', '') for t in (sub.get('tags') or [])]
            studio = next((t for t in tags if t.endswith('STUDIO') or t.endswith('工作室')), '')

        # --- 标签 ---
        tags = [cfg['category']]
        for t in (sub.get('tags') or []):
            n = t.get('name', '')
            if n and n not in tags and not re.match(r'^\d{4}年', n) and len(n) <= 8:
                tags.append(n)
            if len(tags) >= 6:
                break
        tags.insert(0, '进击的巨人')

        # --- 角色谱 ---
        chars = []
        want = set(cfg['chars'])
        found = {}
        for sid2 in SEASON_ORDER:
            for c in chars_raw.get(sid2, []):
                nm = c.get('name')
                if nm in want and nm not in found:
                    found[nm] = c
        for nm in cfg['chars']:
            c = found.get(nm)
            if not c:
                continue
            cn, role, emoji = CHARACTERS.get(nm, (nm, '登场角色', '👤'))
            chars.append(collections.OrderedDict([
                ('name', cn),
                ('roleTitle', role),
                ('avatarEmoji', emoji),
                ('description', clean(c.get('summary', ''))[:180]),
            ]))

        # --- 大纲 ---
        outlines = [
            collections.OrderedDict([('chapterOrder', i + 1), ('title', cfg['outline_titles'][i]),
                                     ('summary', cfg['outlines'][i])])
            for i in range(len(cfg['outlines']))
        ]

        # --- 语录 ---
        notes = [collections.OrderedDict([
            ('content', txt), ('noteType', 'quote'), ('page', title),
            ('chapter', ''), ('createdAt', now),
        ]) for txt in cfg['notes']]

        rating = (sub.get('rating') or {}).get('score')
        date = sub.get('date') or ''

        item = collections.OrderedDict([
            ('title', title),
            ('author', ' · '.join([x for x in (director, studio) if x]) or '谏山创'),
            ('coverUrl', f'covers/bgm_{sid}.webp'),
            ('category', cfg['category']),
            ('status', 'wishlist'),
            ('mediaType', 'anime'),
            ('rating', rating),
            ('tags', tags),
            ('shortComment', cfg['short_comment']),
            ('review', cfg['review']),
            ('startDate', date),
            ('finishDate', ''),
            ('buyChannel', ''),
            ('shelfLocation', ''),
            ('bindingType', ''),
            ('createdAt', now),
            ('updatedAt', now),
            ('sourceType', 'bangumi'),
            ('sourceId', str(sid)),
            ('description', clean(sub.get('summary', ''))),
            ('isDeleted', False),
            ('deletedAt', ''),
            ('notes', notes),
            ('sessions', []),
            ('characters', chars),
            ('outlines', outlines),
            ('locations', []),
            ('audioTracks', []),
            ('mindprint', collections.OrderedDict(cfg['mindprint'])),
        ])
        works.append(item)
        added.append((title, sid, rating, len(chars), len(outlines)))

    preset['worksCount'] = len(works)
    preset['exportedAt'] = now

    with open(PRESET, 'w', encoding='utf-8') as f:
        json.dump(preset, f, ensure_ascii=False, indent=2)
        f.write('\n')

    print(f'=== 新增 {len(added)} 部 ===')
    for t, sid, r, nc, no in added:
        print(f'  {t:<26} bgm={sid:<7} 评分={r}  角色={nc}  大纲={no}')
    if skipped:
        print(f'\n已存在跳过: {skipped}')
    print(f'\n作品总数: {len(works)}（worksCount 已同步）')


if __name__ == '__main__':
    main()
