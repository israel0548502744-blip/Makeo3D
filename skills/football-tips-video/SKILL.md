---
name: "football-tips-video"
description: "Build the user's daily vertical TikTok football predictions video (סרטון תחזיות כדורגל, page יום המזל) from his clips, match images, slip screenshots and voiceover — and write the voiceover script when he asks — in his fixed house style."
---

# סרטוני תחזיות כדורגל (Football predictions video)

The user makes these videos daily. He sends: football clips, match images (two logos on a competition background: blue for Champions League, orange-black for Europa League), betting-slip screenshots (white background: pick + odds), the voiceover MP3 and the voiceover text — or he asks Claude to **write the voiceover** (see workflow step 0), records it in ElevenLabs and sends the MP3. Claude builds the finished 9:16 video. Talk to him in short, simple, everyday Hebrew.

## Fixed rules (never break)
- **Wording: "תחזית/תחזיות", never "המלצה/המלצות"** (since 26.10 — a video was blocked under TikTok's gambling rules). In the script, captions and post text: "התחזית שלי", "התחזיות שלי למשחקים של היום", "לפני התחזית הבאה", "איזו תחזית הכי חזקה?". No "הימורים/הימורי לייב/ווינר/טוטו/betting" words or hashtags. Pick-sentence triggers become "התחזית שלי" / "אני צופה".
- **Never show his betting-slip screenshots** (odds, "סכום", "החזר") — since 26.10 he approved replacing them. He still sends slips: read the match + pick from the slip and put a **prediction card** on the tip beat instead: `"p":{"match":"גלאטסראיי - פנרבחצ'ה","pick":"שתי הקבוצות כובשות<br>+ מעל 2.5 שערים"}` (green pill "התחזית שלי", match line, pick in big green; no odds, no stake). **Card text must match the slip exactly** (he and his partner asked, 27.10 — "לפחות 3" confused people): "מעל 2.5 & כן" → "שתי הקבוצות כובשות + מעל 2.5 שערים"; "מעל 1.5" → "מעל 1.5 שערים"; "1x2 - מחצית ראשונה: X" → "X מנצחת מחצית ראשונה"; "1x2: X" → "X מנצחת"; "קרנות מעל 8" → "מעל 8 קרנות". Use the slip's own numbers, never round them. The **voiceover** may stay in spoken football language ("שתי הקבוצות יכבשו, ויהיו לפחות שלושה שערים") — only the card must be exact.
- Max length **40 seconds** (aim for 39.0s of voice + 0.7s tail).
- Open with a short punchy hook line. **"אל תפספסו את זה!!" is worn out** (he said so 29.10) — offer 4–5 fresh options when writing the script (he picked "הערב הזה מטורף!!"; others: "דרבי היום!!", "תעצרו הכל!!") or use the exact line he writes. Hook style: huge white text with black outline + green glow, punch-zoom in, **phonk hit** sound (distorted 808 drop + kick + clap + down-sweep). **No bells/chimes on the opening** — he wants it cool and hard, like phonk football edits.
- **The first 2 seconds decide everything.** Frame 0 = the most energetic, sharp, colorful moment he sent (players jumping/celebrating, a goal hitting the net) — never a blurry close-up or a slow wide tactical shot. Cut to a second dynamic shot by ~1.2s (when the intro line starts).
- **Match image** pops in **just above the captions** (card bottom at 890px, so it sits mid-upper screen — not up at the top) **the moment the voice says "X נגד Y"**, with a soft bubbly **pop** (he disliked the whoosh noise there). Stays ~1.6s minimum.
- **Prediction card** (`p`) pops in **when the voice starts the pick sentence** ("התחזית שלי", "אני צופה"), with a **soft warm coin** sound (the bright ka-ching was too harsh), and **stays until the end of that sentence**. ~80% of the screen width (top 1110px), green border + glow. **No logo on it.** Make that sentence its own `tip` beat. If the pick sentence also introduces the match ("התחזית שלי לקלאסיקו"), put `m` and `p` on the same tip beat; the card then comes 0.35s after the match image.
- **Story photo** (`"k":"kane.jpg"` on a beat): when he sends a news photo (e.g. Kane kissing the Ballon d'Or) and asks to mention it, write a line about it and put `k` on that beat — the photo pops in (pop sound) centred above the captions with a green frame (500px wide), from the start of the beat to its end (min 1.8s). Facts like this come from him, never invented.
- **No slips sent**: if he sends only match images and says "script everything", choose sensible predictions yourself, list them clearly as your choice and ask him to confirm/send slips before he records.
- **WhatsApp line** ("רק רגע לפני התחזית הבאה, תשימו עוקב ותיכנסו לקבוצת הוואטסאפ...") gets a green card "קבוצת הוואטסאפ שלנו / הקישור בביו".
- **Captions** (style learned from his reference edit, 25.10): Heebo ExtraBold **80px** (he asked for bigger than 66), white with a soft dark shadow (no thick outline), **in the middle of the screen** (top 930px) between the match card and the slip, **max 3 words per chunk, the whole chunk appears at once** (80ms fade), no jumping. Pick words green #38e07b (`hl`); a brand/name word can be cyan #3ec6ff (`hl2`, set per word in timeline). Slip sits lower (top 1110px) so it never covers captions.
- **NO ZOOM AT ALL on clips** (he hates it — "זום מגעיל"). This includes the hidden zoom from stabilization: vidstab `optzoom=1` without limits zoomed ~40% on panning shots. vidstab is capped (`maxshift=8:maxangle=0.004`, zoom ≤ ~3%, static). If any zoom is ever needed, only tiny and slow. After building, compare a frame of each shot with the same source frame to confirm the framing matches.
- **No slow motion**: after every build check the printed shot list — every slowdown factor must be ≤1.05. The hook piece especially must be at least as long as the hook beat (a 1.4s piece under a 1.6s hook played 14% slow on 26.10).
- **No repeated frames / backward jumps**: each clip plays forward continuously; cut only between different clips or forward-only to skip a flash, a logo outro or a blurry close-up.
- **Never use blurry/over-zoomed frames** (extreme close-ups where you can't tell what you're seeing): check the per-second contact sheet and skip those seconds.
- **Screen recordings** (black bars, phone status bar with clock/battery/red record dot): crop them away before planning — **only when the clip really is a screen recording** (he said so 2.11). Detect it first: look at frame 0/mid/end at full size for a status bar (clock, battery, 5G, red record dot / dynamic island) or solid black bars around the picture (cropdetect box clearly smaller than the frame). Normal clips (downloaded/app exports, full-frame picture) are never cropped. cropdetect is fooled by the white status-bar text, and the picture area can drift over the clip. On his iPhone recordings (384×832) the dynamic island / record dot reach down to ~y=50, so start the crop at y≥52 and check the top strip of a cropped frame — measure the first/last bright row and column at several timestamps (e.g. 1, 4, 7, 10, 13s), take the safe inner box, pre-crop to `clips/<name>c.mp4` (x264 CRF 10) and use that file in plan.json.
- **No betting brands on screen** (since 3.11): scan every chosen piece for betting-site ad boards (Unibet, bet365, Winner, "18" age signs, etc.) and skip those seconds — they can get the video flagged. Shirt sponsors that are betting sites (e.g. Roma's Eurobet) can't be removed: prefer wide shots over chest close-ups for those teams and tell him once.
- **No clip for a match**: never use footage of a different, identifiable team (e.g. another club's stadium) — continue with the other matches' remaining footage, and tell him once.
- Skip white flash frames and end-of-clip logo cards inside source clips (YAVG > 165; check the contact sheet for logo outros).
- **Best quality always** (background footage quality is what keeps viewers): lanczos scaling; low-res sources (<1100px tall or <720px wide — 576×1024 TikTok downloads count) get denoise `hqdn3d=2:1.5:4:3` before scaling and `cas=0.7` + light `eq` (contrast 1.07, saturation 1.2) after (approved 29.10, now built into build.py; FSRCNN/EDSR AI upscalers gave no visible gain on CPU), x264 slow CRF 12 for segments, CRF 15 final, AAC 320k. If his clips are low-res (WhatsApp-compressed), tell him once to send originals.
- **Music**: deliver ONE version **without background music**; he adds the song in TikTok. **Only these songs** (his approved list): Metamorphosis – INTERWORLD (his default), Rapture – INTERWORLD, Close Eyes – DVRST, Murder In My Mind – Kordhell, Sahara – Hensonn, Sleepwalker – akiaura, Neon Blade – MoonDeity, Can You Feel My Heart – Bring Me The Horizon (dramatic openings), After Dark – Mr.Kitty (emotional moments). Suggest one per video from this list. Claude cannot download songs (network is blocked). Only add music if he sends the audio file: `--music file --mvol <v> --mstart <drop>`. Set the level by measurement, not a fixed number: music mean ≈ **8 dB under the voice** (he asked for it a bit louder than 12 dB under; e.g. a loud -9 dB song → mvol 0.13). Start the song at its beat drop (find the RMS jump) so the drop lands on the hook at frame 0.
- No "18+" line unless he asks.
- When unsure about anything that changes the result (which clip is which team, missing clip, wording), ask before building. Never guess a team from a clip: check jerseys/logos frame by frame with timestamps, and ask if unclear.

## Lessons from 29.10–2.11
- **Never use over-zoomed close-ups of the ball in the net or a keeper dive filmed through the net** — blurry, you can't tell what's happening. He flagged them twice; check every chosen piece on a contact sheet and swap such shots for a clear attack/shot/celebration.
- **Don't open on Hapoel Tel Aviv (red) footage** — many viewers hate the team. For hooks and openers prefer big neutral clubs (Barça / Real Madrid / a star celebrating) when he has such footage.
- **Clip sources**: his screen recordings and app downloads come in tiny (384–464px wide). Tell him to download with a Telegram link-downloader bot at the highest quality and send it as a File/Document, not to screen-record.
- Spoken corners pick: "לפחות תשע קרנות" (spelling: קרנות, not קרניות).
- Before cutting a source clip, find its internal cuts (YAVG jump per frame) so a piece doesn't start on a frame of the previous scene.

## Special one-off projects (only when he asks; daily tips rules above stay as they are)
- **Promo / brand videos** (e.g. 15s ad for יום המזל, 1.11): brand elements are allowed here. Brand: name יום המזל 🍀, green #38e07b on dark green #062819, TikTok @YOM.HAMZEL, Instagram @yomhamzel, WhatsApp community (free), tagline "אולי זה יום המזל שלכם", stamp "חיים את המשחק", analyst יקיר האנליסט. Use the clover logo `assets/clover_logo.webp` (from his site) — **not** the round `assets/logo.png` badge, whose betting slip / dice / coins could get the video flagged. Still no betting words.
- **Beat-synced music edits** (he sends a song): no voice, no captions except brand at the end. Detect bass onsets (spectral flux 30–160 Hz) and cut every 0.46–0.78s on the hits, starting the song at its drop (RMS jump) on frame 0. Only peak moments (shots, goals, saves, celebrations), forward-only pieces, no overlaps per clip. Mix the song to ≈ -11 LUFS, 0.6s fade at the end.
- **"Crazy" collage edit style** (his reference, Ronaldo-style): chrome 3D Hebrew title building letter by letter, cut-out rectangle strips from a frame a few frames ahead (scaled 1.25) on each beat, horizontal motion-blur whips around cuts, white flash on big hits, short shake + RGB split on each cut, a split screen of two clips. Still no zoom on the clips.
- Brand overlays he liked on edits: "התחזית שלי" chips (clover logo + match + pick) shown **only over footage of that match** (group consecutive pieces of the same match), green sparks on celebrations, the stamp on a goal, and an end card styled as a profile: clover logo in a circle with a rotating green ring, @YOM.HAMZEL, a "עקבו" button that turns into "במעקב ✓", tagline.

## Brand / ending (since 26.10)
- Videos are for his TikTok page **יום המזל**, but **no logo anywhere in the video** (no watermark, no end logo) and **no "בהצלחה לכולם / יום ירוק" closing line** — videos with them kept getting removed.
- **The video always ends right after the comments CTA** ("איזו תחזית הכי חזקה? תכתבו בתגובות!") + 0.7s tail. Never add anything after it. If his voiceover still has a closing line after the CTA, drop that beat and cut the voice right after the CTA (short fade).
- Green #38e07b accents stay (slip border/glow, hook glow, highlighted words).

## Editing style (learned from his reference videos, 25.10 — his own best + competitor "הכריש")
- **Fast pace**: a new shot every ~1.5–2.5s (competitors cut every ~1.5–2s). Use several forward-only pieces per section (different moments/clips), never one long shot of 10s+. Cut on phrase breaks.
- **B-roll matches the words**: goal/net shot on "שערים"/"כובשות", celebration on the pick, crowd/fans on "משחק פתוח", close-up face on emotional lines. Close-ups and celebrations beat wide tactical shots.
- **Strong first frame**: hook on a big energetic celebration or a goal (sharp, colorful, lots of motion) — not a blurry close-up, not a calm wide shot. Caption visible from frame 1.
- **Comment CTA is the ending**: always its own last beat.
- Competitors zoom in hard on faces — he wants **no heavy zoom**; keep our no-zoom rule.
- Sound design: pleasant, punchy, never hissy noise. Effects: phonk hit (hook), pop (match card), soft coin (slip).
- His reference was 64s; his own tips videos stay ≤40s (fixed rule).

## His saved choices (control page)
Control page artifact: https://claude.ai/artifact/8zmzrbfAPGTgjxvBUFEysS (db doc `studio/v2`, read with ArtifactData get collection `studio` doc `v2`). Current picks (updated 26.10 from his chat corrections — **these rules in this file win over older values on the control page**): punch-zoom text on the hook (text only, never the clip), phonk hit on hook, soft pop on match card, soft coin on the prediction card, prediction card (no betting slips) at ~80% width with green frame, "תחזית" wording, no logo, video ends on the comments CTA, WhatsApp green card, male energetic voice, speed-up if over 40s, Heebo ExtraBold 80px, 3-word chunk captions (whole chunk at once), green highlight, middle of screen, no zoom on clips, no 18+. If he changes a choice there, read it before building.

## Workflow
0. **Writing the voiceover** (when he asks "תכתוב לנו את הקריינות"): from his slips + match images + clips. First inspect the clips (which team, best moments) so the order fits the footage.
   - Structure: hook "אל תפספסו את זה!!" → "אלו התחזיות שלי למשחקים של היום!" → per match: "X נגד Y." + one short line of atmosphere/opinion + "התחזית שלי: <pick as football outcome>!" → WhatsApp line **before the last match** ("רק רגע לפני התחזית האחרונה, תשימו עוקב ותיכנסו לקבוצת הוואטסאפ שלנו, הקישור בביו!") → last match → **ends with** "איזו תחזית הכי חזקה? תכתבו בתגובות!" (nothing after it).
   - Order: the hottest match first (derby/big game); the match with the best footage gets more screen time.
   - ~85–95 words ≈ 38–39s of his ElevenLabs voice. Vary the pick opener ("התחזית שלי", "גם כאן אני צופה").
   - **Never invent facts/stats** (form, streaks, injuries) — only opinion/atmosphere, unless he gives a real fact.
   - No betting words at all (המלצה, הימור, יחס, מעל/מתחת as bet types, ווינר).
   - Give him two copy blocks: plain text, and an ElevenLabs version with `[excited] [shouting]` before the hook and `[excited]` before the rest.
1. **Collect** clips, match images, slip images, text, voice MP3. Map each image to its match (by the names on the slip). He usually tells you which clip is which match and where the last pick goes.
2. **Inspect clips**: per-second contact sheet with timestamps
   `ffmpeg -i clip.mp4 -vf "fps=1,scale=160:-1,drawtext=text='%{pts\:hms}':x=4:y=4:fontsize=14:fontcolor=yellow:box=1:boxcolor=black,tile=10x3" -frames:v 1 sheet.jpg`
   Identify team per clip and good moments (goal, celebration, logo outro to cut). Check md5 of uploads (he may resend the same file). Find flash frames:
   `ffmpeg -i clip.mp4 -vf "signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=-" -f null - | paste - - | sed -E 's/.*pts_time:([0-9.]+).*YAVG=([0-9.]+)/\1 \2/' | awk '$2>165'`
3. **Script** → `<proj>/script.json`: list of beats `{"type": hook|line|match|tip|outro, "vo": text, "hl": words to color green, "m": match image file, "p": prediction card {match, pick} (use this; "s" slip image is legacy, do not use), "wa": true}`. Remove emojis. Fix only small spelling (כשמדובר, להיכנס, וואטסאפ, ברוז'). The text must be what the voice actually says, in order. Punctuation matters: every `, : . ! ?` is a phrase break used for timing.
   Example: `[{"type":"hook","vo":"אל תפספסו את זה!!"},{"type":"line","vo":"אלו התחזיות שלי למשחקים של היום!"},{"type":"match","vo":"באיירן מינכן נגד ארסנל.","m":"m-bayern.jpg"},{"type":"line","vo":"..."},{"type":"tip","vo":"התחזית שלי: שתי הקבוצות יכבשו, ולפחות שלושה שערים!","hl":"שתי הקבוצות יכבשו, ולפחות שלושה שערים","p":{"match":"באיירן מינכן - ארסנל","pick":"שתי הקבוצות כובשות<br>+ מעל 2.5 שערים"}},{"type":"line","vo":"רק רגע לפני התחזית האחרונה, תשימו עוקב ותיכנסו לקבוצת הוואטסאפ שלנו, הקישור בביו!","hl":"לקבוצת הוואטסאפ","wa":true},{"type":"line","vo":"איזו תחזית הכי חזקה? תכתבו בתגובות!","hl":"תכתבו בתגובות"}]`  ← the CTA is always the last beat
4. **Voice**: usually he sends the ElevenLabs MP3 himself. If he asks Claude to make it (ElevenLabs connector): `creative_create_flow`, then `creative_generate_speech` with model `eleven_v4`, voice **Itai – Upbeat Social Creator `JIxTgeeS5w0UQyBxEnrl`** (Hebrew), prompt = `[excited] [shouting] <hook> [excited] <rest of text>`. The audio URL is on storage.googleapis.com, which is blocked here, so give him the flow link and ask him to download the MP3 and attach it (his free tier was flagged once; if generation fails, tell him plainly). Save it as `<proj>/vo_raw.mp3`.
5. **Set up** (once per session): write the tool files below into `/home/claude/fv/tools/`, run `python3 sfx.py` there, (the logo in `assets/` is kept only for reference; it is not used in videos). Font: `curl -sSL -o <proj>/fonts/Heebo.ttf "https://raw.githubusercontent.com/google/fonts/main/ofl/heebo/Heebo%5Bwght%5D.ttf"` (raw.githubusercontent works; github.com/raw does not). Project dir: `/home/claude/fv/<name>/` with `script.json`, `plan.json`, `img/` (match + slip images), `fonts/Heebo.ttf`, `vo_raw.mp3`, and a copy of `overlay.html`.
6. **plan.json** – which clip plays under which part. A section starts at a beat index and runs to the next section; pieces are `[path, start_sec, available_sec]` and play forward back-to-back:
   `{"sections":[{"from":0,"pieces":[[celebration_clip,12.5,1.3]]},{"from":1,"pieces":[[teamA_clip,0,30]]},{"from":5,"pieces":[[teamB_clip,0,12.1],[teamB_clip,13.7,5.0]]},{"from":10,"pieces":[[teamC_clip,12,15.5]]}]}`
   Hook = strongest celebration (take it from a part of a clip that won't be used again). Each match section uses that match's clip from its "X נגד Y" beat; the WhatsApp line can keep running the previous match's clip. Keep slowdown under ~5% (give enough footage).
7. **Run**:
   `python3 tools/align.py <proj> 39` (maps phrases to voiced parts by letter-rate fit: a phrase may span several parts when the voice pauses mid-sentence, or phrases may share a part when it skips a comma; prints the span/share cases and fit cost — check each phrase's time looks plausible)
   `node tools/render.js <proj>/timeline.json <proj>/ov <end+0.7> 30`
   `python3 tools/build.py <proj> <proj>/video_final.mp4` — at best quality this takes ~10 minutes: run it with `run_in_background` and tell him it's rendering.
8. **Check before sending**: grid of frames at hook, each match card, each prediction card, WhatsApp card, the final CTA (`ffmpeg -ss T -frames:v 1`, hstack, Read the image). Confirm duration ≤ 40, no flash frames, captions readable, cards on time. Then SendUserFile one version without music, and list in one short message what's in it and what to check (e.g. voice speed-up factor).
   Tell him once (short) how to keep TikTok quality: move the file to the phone directly (download from here / Drive, not through WhatsApp, which re-compresses); in TikTok turn on **Upload HD** (More options → Upload quality / "העלאה באיכות HD"); don't add TikTok filters or re-trim inside the app (re-encodes).
   **Sending the file to someone** (he asked): in Telegram send it as **File** (📎 → File), not as gallery video, so it isn't compressed; in WhatsApp send as **Document**; or share a Google Drive link. Don't zip/compress it.
   **Post text**: give him a ready-to-paste TikTok description: short hook line + one line per pick with emoji + a question for comments ("איזו תחזית הכי חזקה? תכתבו בתגובות 👇") + WhatsApp line "הקישור בביו". **No hashtags at all** (he said they're not needed). Suggest one song from his approved list.
9. He replies with fixes; apply and re-render (`--skipbase` reuses clips if only overlay/audio changed).

## Tool files

### tools/align.py
```python
"""Tighten pauses, speed voiceover to fit the max length, build timeline.json.
usage: python3 align.py <projdir> [max_seconds=39]
Needs <projdir>/script.json and <projdir>/vo_raw.mp3 (or .wav).
Each beat's text is split into phrases at , : . ! ? ; every pause in the recording should match a phrase break.
If the counts don't match it tries other silence settings, then falls back to proportional timing."""
import json, re, subprocess, sys, os, numpy as np, wave
P=sys.argv[1].rstrip('/')+'/'; TARGET=float(sys.argv[2]) if len(sys.argv)>2 else 39.0; GAP=0.10
S=json.load(open(P+'script.json'))
clean=lambda s: re.sub(r'[.,:!?]','',s)
PH=[]
for bi,b in enumerate(S):
    cur=[]
    for w in b['vo'].split():
        cur.append(w)
        if re.search(r'[,:.!?]$',w): PH.append((bi,' '.join(cur))); cur=[]
    if cur: PH.append((bi,' '.join(cur)))
src=P+('vo_raw.mp3' if os.path.exists(P+'vo_raw.mp3') else 'vo_raw.wav')
subprocess.run(['ffmpeg','-y','-v','error','-i',src,'-ac','1','-ar','48000',P+'vo_mono.wav'],check=True)
w=wave.open(P+'vo_mono.wav'); SR=w.getframerate(); x=np.frombuffer(w.readframes(w.getnframes()),np.int16).astype(np.float32); dur=len(x)/SR
def voiced_for(n,d):
    out=subprocess.run(['ffmpeg','-i',P+'vo_mono.wav','-af',f'silencedetect=n={n}dB:d={d}','-f','null','-'],capture_output=True,text=True).stderr
    st=[float(v) for v in re.findall(r'silence_start: ([\d.]+)',out)]; en=[float(v) for v in re.findall(r'silence_end: ([\d.]+)',out)]
    vo=[]; p=0.0
    for a,e in zip(st,en+[dur]*(len(st)-len(en))):
        if a>p+0.03: vo.append((p,a))
        p=e
    if p<dur-0.03: vo.append((p,dur))
    return vo
L=[sum(max(1,len(clean(q))) for q in txt.split()) for _,txt in PH]
N=len(PH)
def fit(v):
    # map phrases onto voiced parts: a phrase may span several parts (voice paused mid-sentence)
    # or several phrases may share one part (voice skipped a comma pause). DP on letter-rate fit.
    K=len(v); rate=(v[-1][1]-v[0][0])/sum(L); INF=float('inf')
    dp=[[INF]*(K+1) for _ in range(N+1)]; bk=[[None]*(K+1) for _ in range(N+1)]; dp[0][0]=0
    for p in range(N):
        for i in range(K):
            if dp[p][i]==INF: continue
            for j in range(i+1,min(K,i+6)+1):          # phrase p spans parts i..j-1
                d=v[j-1][1]-v[i][0]; c=dp[p][i]+((L[p]*rate-d)/d)**2+0.15*(j-i-1)
                if c<dp[p+1][j]: dp[p+1][j]=c; bk[p+1][j]=(p,i,'span')
            for q in range(p+2,min(N,p+4)+1):          # phrases p..q-1 share part i
                d=v[i][1]-v[i][0]; c=dp[p][i]+((sum(L[p:q])*rate-d)/d)**2+0.15*(q-p-1)
                if c<dp[q][i+1]: dp[q][i+1]=c; bk[q][i+1]=(p,i,'share')
    if dp[N][K]==INF: return INF,None
    out=[]; p,j=N,K
    while p>0:
        pp,i,kind=bk[p][j]; out.append((pp,p,i,j,kind)); p,j=pp,i
    return dp[N][K]/N,out[::-1]
best=None
for n in (-38,-35,-40,-32,-42,-45):
    for d in (0.12,0.10,0.15,0.08,0.18,0.22):
        v=[iv for iv in voiced_for(n,d) if iv[1]-iv[0]>0.08]
        if not v: continue
        c,m=fit(v)
        if m and (best is None or c<best[0]): best=(c,v,m,n,d)
cost,voiced,MAP,bn,bd=best
print(f'phrases {N}  voiced parts {len(voiced)}  silence {bn}dB/{bd}s  fit cost {cost:.3f}')
pad=0.03; parts=[]; partpos=[]; t=0.0
for i,(a,e) in enumerate(voiced):
    a2=max(0,a-pad); e2=min(dur,e+pad); seg=x[int(a2*SR):int(e2*SR)]
    partpos.append((t+(a-a2), t+(a-a2)+(e-a))); parts.append(seg); t+=len(seg)/SR
    if i<len(voiced)-1: parts.append(np.zeros(int(GAP*SR),np.float32)); t+=GAP
y=np.concatenate(parts); tight=len(y)/SR; tempo=max(1.0,tight/TARGET)
wo=wave.open(P+'vo_tight.wav','wb'); wo.setnchannels(1); wo.setsampwidth(2); wo.setframerate(SR); wo.writeframes(y.astype(np.int16).tobytes()); wo.close()
subprocess.run(['ffmpeg','-y','-v','error','-i',P+'vo_tight.wav','-af',f'atempo={tempo:.4f}','-ar','48000',P+'vo_final.wav'],check=True)
print(f'raw {dur:.2f}s  tight {tight:.2f}s  speed x{tempo:.3f}  final {tight/tempo:.2f}s')
newpos=[]
for p,q,i,j,kind in MAP:
    a,e=partpos[i][0],partpos[j-1][1]
    if kind=='span': newpos.append((a,e))
    else:
        tot=sum(L[p:q]); c=0
        for r in range(p,q): s0=a+(e-a)*c/tot; c+=L[r]; newpos.append((s0,a+(e-a)*c/tot))
    if kind=='share' or j-i>1: print(' ',kind,j-i,'parts:',' | '.join(PH[r][1] for r in range(p,q))[:60])
beats=[dict(b,words=[],start=None,end=None,phrases=[]) for b in S]
for (bi,txt),(a,e) in zip(PH,newpos):
    a/=tempo; e/=tempo; ws=txt.split(); L=sum(max(1,len(clean(q))) for q in ws); tt=a
    hl=set(clean(q) for q in S[bi].get('hl','').split()); ph=[]
    for q in ws: ph.append({'w':q,'t':round(tt,3),'hl':clean(q) in hl}); tt+=(e-a)*max(1,len(clean(q)))/L
    B=beats[bi]; B['phrases'].append({'start':round(a,3),'end':round(e,3),'words':ph}); B['words']+=ph
    B['start']=round(a,3) if B['start'] is None else B['start']; B['end']=round(e,3)
json.dump(beats,open(P+'timeline.json','w'),ensure_ascii=False,indent=1)
for b in beats: print(f"{b['start']:6.2f}-{b['end']:6.2f} {b['type']:6} {b['vo'][:40]}")
```

### tools/render.js
```javascript
// node render.js <timeline.json> <outdir> <duration> [fps]
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const [tlPath, outDir, durS, fpsS] = process.argv.slice(2);
  const fps = +(fpsS || 30), dur = +durS, N = Math.ceil(dur * fps);
  fs.rmSync(outDir, { recursive: true, force: true }); fs.mkdirSync(outDir, { recursive: true });
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1080, height: 1920 } });
  // overlay.html is copied into the project dir so img/ and fonts/ resolve there
  await p.goto('file://' + path.resolve(path.dirname(tlPath), 'overlay.html'));
  await p.evaluate(tl => setTL(tl), JSON.parse(fs.readFileSync(tlPath, 'utf8')));
  await p.evaluate(() => document.fonts.ready);
  // preload images
  const imgs = [...new Set(JSON.parse(fs.readFileSync(tlPath, 'utf8')).flatMap(b => [b.m, b.s, b.k].filter(Boolean)).concat(['logo.png']))];
  await p.evaluate(list => Promise.all(list.map(n => new Promise(r => { const i = new Image(); i.onload = i.onerror = r; i.src = 'img/' + n; }))), imgs);
  if (process.env.WA_SUB) await p.evaluate(v => { WA_SUB = v; }, process.env.WA_SUB);
  let prevKey = null, prevFile = null, shots = 0;
  for (let f = 0; f < N; f++) {
    const key = await p.evaluate(t => render(t), f / fps);
    const file = path.join(outDir, `ov_${String(f).padStart(5, '0')}.png`);
    if (key === prevKey && prevFile) fs.linkSync(prevFile, file);
    else { await p.screenshot({ path: file, omitBackground: true }); prevKey = key; prevFile = file; shots++; }
  }
  await b.close(); console.log(`frames ${N}, screenshots ${shots}`);
})();
```

### tools/overlay.html
```html
<!doctype html><html dir="rtl"><head><meta charset="utf-8">
<style>
@font-face{font-family:H;src:url(fonts/Heebo.ttf);font-weight:100 900}
html,body{margin:0;width:1080px;height:1920px;background:transparent;overflow:hidden;font-family:H,sans-serif}
#stage{position:absolute;inset:0}
.hook{position:absolute;left:40px;right:40px;top:0;bottom:0;display:flex;align-items:center;justify-content:center;text-align:center;
  font-weight:900;font-size:150px;line-height:1.05;color:#fff;-webkit-text-stroke:14px #000;paint-order:stroke fill;
  text-shadow:0 0 38px rgba(56,224,123,.75),0 10px 30px rgba(0,0,0,.6)}
.cap{position:absolute;left:0;right:0;top:930px;display:flex;justify-content:center;white-space:nowrap;font-weight:800;font-size:80px;line-height:1.2;color:#fff;
  text-shadow:0 0 3px #000,0 2px 4px rgba(0,0,0,.95),0 4px 14px rgba(0,0,0,.7)}
.cap .ln{transform-origin:50% 50%}.cap span{margin:0 9px;display:inline-block}
.cap .hl{color:#38e07b}.cap .hl2{color:#3ec6ff}
.card{position:absolute;left:60px;right:60px;bottom:1030px;border-radius:22px;overflow:hidden;box-shadow:0 24px 60px rgba(0,0,0,.65);transform-origin:50% 50%}
.card img{display:block;width:100%}
.pic{position:absolute;left:250px;right:250px;bottom:1030px;border-radius:26px;overflow:hidden;border:6px solid #38e07b;box-shadow:0 0 40px rgba(56,224,123,.55),0 24px 60px rgba(0,0,0,.65);transform-origin:50% 50%}
.pic img{display:block;width:100%}
.slipw{position:absolute;left:110px;right:110px;top:1110px;transform-origin:50% 50%}
.slip{border-radius:14px;overflow:hidden;border:5px solid #38e07b;box-shadow:0 0 34px rgba(56,224,123,.55),0 22px 50px rgba(0,0,0,.6)}
.slip img{display:block;width:100%}
.pred{background:linear-gradient(180deg,rgba(14,30,20,.96),rgba(6,16,10,.96));padding:26px 34px 30px;text-align:center;color:#fff}
.pred .tag{display:inline-block;background:#38e07b;color:#06301a;font-weight:900;font-size:36px;padding:4px 22px;border-radius:30px;margin-bottom:14px}
.pred .mt{font-weight:700;font-size:40px;opacity:.9;margin-bottom:8px}
.pred .pk{font-weight:900;font-size:54px;line-height:1.15;color:#38e07b}
.wm{position:absolute;right:42px;top:78px;width:124px;height:124px;filter:drop-shadow(0 4px 10px rgba(0,0,0,.6))}
.logo{position:absolute;left:300px;width:480px;top:370px;transform-origin:50% 50%;filter:drop-shadow(0 0 40px rgba(56,224,123,.65)) drop-shadow(0 20px 40px rgba(0,0,0,.6))}
.wa{position:absolute;left:80px;right:80px;top:560px;background:#25d366;border-radius:40px;padding:34px 40px;display:flex;align-items:center;gap:30px;
  box-shadow:0 22px 50px rgba(0,0,0,.55);transform-origin:50% 50%;color:#06301a}
.wa svg{flex:none;width:130px;height:130px}
.wa b{display:block;white-space:nowrap;font-size:58px;font-weight:900;line-height:1.05}
.wa small{display:block;font-size:44px;font-weight:700;opacity:.85;margin-top:8px}
</style></head><body><div id="stage"></div>
<script>
let TL=null;
const stage=document.getElementById('stage');
function pop(p){ // 0..1 -> scale with overshoot
  if(p>=1)return 1; if(p<=0)return .55; const s=1.70158*1.3,x=p-1; return .55+.45*(1+(s+1)*x*x*x+s*x*x)}
let CH=[];let WA_SUB="הקישור בביו";
function setTL(tl){TL=tl;CH=[];
  TL.forEach((b,bi)=>{if(bi===0)return;(b.phrases||[{words:b.words,end:b.end}]).forEach(ph=>{
    const ws=ph.words,n=Math.ceil(ws.length/3),size=Math.ceil(ws.length/n);   // balanced chunks of <=3 words
    for(let i=0;i<ws.length;i+=size){const part=ws.slice(i,i+size);const last=i+size>=ws.length;
      CH.push({words:part,t0:part[0].t,t1:last?ph.end:ws[i+size].t})}})})}
function render(t){
  let html='',key='';
  const hb=TL[0];
  if(t<hb.end+0.05){const p=Math.min(1,t/0.42);const sc=p<1?2.4-1.4*(1-Math.pow(1-p,3)):1;const sc2=p<1?sc:(1+0.03*Math.sin((t-0.42)*6));
    html+=`<div class="hook" style="transform:scale(${sc2.toFixed(3)});opacity:${Math.min(1,p*2.5).toFixed(2)}">${hb.vo}</div>`;key+='h'+sc2.toFixed(3)}
  for(const b of TL){
    if(b.type==='hook')continue;
    if(b.m){const s=b.start,e=Math.max(b.end,s+1.6);if(t>=s&&t<e){const sc=pop((t-s)/0.35);
      html+=`<div class="card" style="transform:scale(${sc.toFixed(3)})"><img src="img/${b.m}"></div>`;key+='m'+b.m+sc.toFixed(3)}}
    // b.k = a story photo (e.g. a player with a trophy) that pops in when the voice starts this beat
    if(b.k){const s=b.start,e=Math.max(b.end,s+1.8);if(t>=s&&t<e){const sc=pop((t-s)/0.35);
      html+=`<div class="pic" style="transform:scale(${sc.toFixed(3)})"><img src="img/${b.k}"></div>`;key+='k'+b.k+sc.toFixed(3)}}
    if(b.s||b.p){const s=b.start+(b.m?0.35:0),e=b.end+0.25;if(t>=s&&t<e){const sc=pop((t-s)/0.35);
      // b.p = prediction card {match, pick} (no odds/stake); b.s = slip screenshot (legacy)
      const inner=b.p?`<div class="pred"><div class="tag">התחזית שלי</div><div class="mt">${b.p.match}</div><div class="pk">${b.p.pick}</div></div>`:`<img src="img/${b.s}">`;
      html+=`<div class="slipw" style="transform:scale(${sc.toFixed(3)})"><div class="slip">${inner}</div></div>`;key+='s'+(b.s||b.p.pick)+sc.toFixed(3)}}
    if(b.logo){const s=b.start;if(t>=s){const sc=pop((t-s)/0.4);const br=1+0.025*Math.sin((t-s)*5);
      html+=`<img class="logo" src="img/logo.png" style="transform:scale(${(sc*br).toFixed(3)})">`;key+='L'+(sc*br).toFixed(3)}}
    if(b.wa){const s=b.start+0.15,e=b.end;if(t>=s&&t<e){const sc=pop((t-s)/0.35);
      html+=`<div class="wa" style="transform:scale(${sc.toFixed(3)})"><svg viewBox="0 0 100 100"><circle cx="50" cy="48" r="40" fill="#fff"/><path d="M22 86 L30 64 L44 78 Z" fill="#fff"/>
      <path d="M38 30c2-2 5-2 6 1l3 7c1 2 0 4-2 5l-2 2c2 5 6 9 11 11l2-2c1-2 3-3 5-2l7 3c3 1 3 4 1 6l-3 3c-4 4-14 1-23-8s-12-19-8-23z" fill="#25d366"/></svg>
      <div><b>קבוצת הוואטסאפ שלנו</b><small>${WA_SUB}</small></div></div>`;key+='w'+sc.toFixed(3)}}
  }
  // captions: whole line is laid out up front, words are only revealed -> nothing ever moves
  let ci=-1;for(let k=0;k<CH.length;k++)if(t>=CH[k].t0)ci=k;
  if(ci>=0){const c=CH[ci];const nxt=ci+1<CH.length?CH[ci+1].t0:1e9;const hide=Math.min(nxt,c.t1+0.45);
    if(t<hide){let rk='';
      // whole chunk appears at once (reference style), quick 80ms fade
      const o=Math.max(0,Math.min(1,(t-c.t0)/0.08));rk=o.toFixed(1);
      const sp=c.words.map(w=>`<span class="${w.hl?'hl':w.hl2?'hl2':''}">${w.w}</span>`).join(' ');
      html+=`<div class="cap" style="opacity:${o.toFixed(2)}"><div class="ln" id="cap">${sp}</div></div>`;key+='c'+ci+'_'+rk}}
  stage.innerHTML=html;
  const cap=document.getElementById('cap');if(cap){const w=cap.scrollWidth;if(w>940)cap.style.transform=`scale(${(940/w).toFixed(3)})`}
  return key}
</script></body></html>
```

### tools/build.py
```python
"""Build the final video: clips + overlay frames + voice + sound effects (+ optional music).
usage: python3 build.py <projdir> out.mp4 [--music song.mp3 --mvol 0.21 --mstart 0] [--skipbase]
<projdir>/plan.json: {"sections":[{"from":<beat index>,"pieces":[["/abs/clip.mp4",start_sec,available_sec],...]}, ...]}
A section runs from its beat's start to the next section's beat start (last one to the end).
Pieces play forward only, back to back, never repeating frames. If a section is longer than its
pieces, the last piece plays slightly slower to fill (keep it under ~5% by picking enough footage)."""
import json, subprocess, os, argparse
ap=argparse.ArgumentParser(); ap.add_argument('proj'); ap.add_argument('out'); ap.add_argument('--music'); ap.add_argument('--mvol',type=float,default=0.21)
ap.add_argument('--mstart',type=float,default=0.0); ap.add_argument('--skipbase',action='store_true'); ap.add_argument('--nostab',action='store_true')
a=ap.parse_args(); P=os.path.abspath(a.proj)+'/'; T=os.path.dirname(os.path.abspath(__file__))+'/'
os.makedirs(P+'seg',exist_ok=True)
TL=json.load(open(P+'timeline.json')); PL=json.load(open(P+'plan.json'))
D=round(TL[-1]['end']+0.7,2)
def run(c): subprocess.run(c,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
secs=PL['sections']; bounds=[0.0 if s['from']==0 else TL[s['from']]['start'] for s in secs]+[D]
shots=[]
for i,s in enumerate(secs):
    left=bounds[i+1]-bounds[i]
    for k,(src,ss,avail) in enumerate(s['pieces']):
        last=k==len(s['pieces'])-1; d=left if last else min(avail,left); f=max(1.0,d/avail)
        shots.append((src,ss,d,f)); left-=d
        if left<=0.01: break
z="setsar=1,fps=30,format=yuv420p"   # no zoom, ever
if not a.skipbase:
  segs=[]
  for i,(src,ss,d,f) in enumerate(shots):
    o=P+f'seg/s{i:02d}.mp4'; segs.append(o); tf=P+f'seg/t{i:02d}.trf'; ind=f'{d/f+0.1:.3f}'
    stab=''
    if not a.nostab:
        run(['ffmpeg','-y','-ss',str(ss),'-t',ind,'-i',src,'-vf',f'vidstabdetect=shakiness=6:accuracy=12:result={tf}','-f','null','-'])
        stab=f'vidstabtransform=input={tf}:smoothing=20:maxshift=8:maxangle=0.004:optzoom=1,'   # capped: zoom never above ~3%
    # scale to cover 1080x1920 (works for any source aspect), best-quality settings
    # low-res sources (WhatsApp) get a light sharpen after upscaling
    h=int(subprocess.run(['ffprobe','-v','error','-select_streams','v:0','-show_entries','stream=height','-of','csv=p=0',src],capture_output=True,text=True).stdout.strip() or 1920)
    w=int(subprocess.run(['ffprobe','-v','error','-select_streams','v:0','-show_entries','stream=width','-of','csv=p=0',src],capture_output=True,text=True).stdout.strip() or 1080)
    low=h<1100 or w<720   # 576x1024 phone/TikTok downloads are low-res too
    pre='hqdn3d=2:1.5:4:3,' if low else ''   # clean noise before upscaling
    sharp='cas=0.7,eq=contrast=1.07:saturation=1.2:gamma=0.97,' if low else 'eq=contrast=1.04:saturation=1.1,'
    vf=stab+pre+f"setpts=PTS*{f:.4f},scale=1080:1920:force_original_aspect_ratio=increase:flags=lanczos,crop=1080:1920,"+sharp+z
    run(['ffmpeg','-y','-ss',str(ss),'-t',ind,'-i',src,'-vf',vf,'-t',f'{d:.3f}','-an','-c:v','libx264','-preset','slow','-crf','12',o])
  open(P+'seg/list.txt','w').write(''.join(f"file '{s}'\n" for s in segs))
  run(['ffmpeg','-y','-f','concat','-safe','0','-i',P+'seg/list.txt','-c','copy',P+'seg/base.mp4'])
print('shots',[(os.path.basename(s)[:12],ss,round(d,2),round(f,3)) for s,ss,d,f in shots],'duration',D)
# sounds: phonk hit on the hook, soft pop on match cards, soft coin on slips, sparkle on the logo end card
ev=[(0.0,'phonk',0.85)]+[(b['start'],'pop',0.55) for b in TL if b.get('m') or b.get('k')]+[(b['start']+(0.4 if b.get('m') else 0.05),'coin',0.6) for b in TL if b.get('s') or b.get('p')]+[(b['start'],'sparkle',0.5) for b in TL if b.get('logo')]
ins=['-i',P+'seg/base.mp4','-framerate','30','-i',P+'ov/ov_%05d.png','-i',P+'vo_final.wav']; fl=['[2:a]volume=1.0,apad[vo]']; mix=['[vo]']; n=3
if a.music:
    ins+=['-ss',str(a.mstart),'-stream_loop','-1','-i',a.music]; fl.append(f'[{n}:a]volume={a.mvol},afade=t=in:d=0.3,afade=t=out:st={D-1.2}:d=1.2[mu]'); mix.append('[mu]'); n+=1
for j,(t,name,v) in enumerate(ev):
    ins+=['-i',T+f'sfx/{name}.wav']; ms=max(0,int(t*1000)); fl.append(f'[{n}:a]volume={v},adelay={ms}|{ms}[e{j}]'); mix.append(f'[e{j}]'); n+=1
fl.append(f"{''.join(mix)}amix=inputs={len(mix)}:normalize=0:duration=longest,atrim=0:{D},alimiter=limit=0.95[aout]")
fl.insert(0,'[0:v][1:v]overlay=0:0:shortest=0,format=yuv420p[vout]')
run(['ffmpeg','-y',*ins,'-filter_complex',';'.join(fl),'-map','[vout]','-map','[aout]','-t',str(D),'-c:v','libx264','-preset','slow','-crf','15','-profile:v','high',
     '-c:a','aac','-b:a','320k','-movflags','+faststart',a.out])
print('done',a.out)
```

### tools/sfx.py
```python
import numpy as np, wave, sys, os
OUT=os.path.join(os.path.dirname(os.path.abspath(__file__)),'sfx'); os.makedirs(OUT,exist_ok=True)
SR=48000
def save(name,x):
    x=np.clip(x/ (np.max(np.abs(x))+1e-9)*0.9,-1,1)
    d=(x*32767).astype(np.int16); d=np.stack([d,d],1)
    w=wave.open(name,'wb');w.setnchannels(2);w.setsampwidth(2);w.setframerate(SR);w.writeframes(d.tobytes());w.close()
def env(n,a,dec):
    t=np.arange(n)/SR; e=np.minimum(t/a,1)*np.exp(-t/dec); return e
rng=np.random.default_rng(1)
def bandnoise(dur,f1,f2,q=2.0):
    n=int(SR*dur); x=rng.standard_normal(n); out=np.zeros(n)
    # time-varying resonant bandpass (state variable filter)
    low=band=0.0
    fr=np.geomspace(f1,f2,n)
    for i in range(n):
        f=2*np.sin(np.pi*fr[i]/SR); hp=x[i]-low-band/q; band+=f*hp; low+=f*band; out[i]=band
    return out
# whoosh: rising then falling band noise, swell envelope
n=int(SR*.6); t=np.arange(n)/SR
w=bandnoise(.6,350,3500,1.5)*np.sin(np.pi*t/.6)**2
save(OUT+'/whoosh.wav',w)
# ka-ching: drawer clunk + two bright bell hits
n=int(SR*1.4); t=np.arange(n)/SR; x=np.zeros(n)
clunk=rng.standard_normal(int(SR*.07))*np.exp(-np.arange(int(SR*.07))/SR/.015); x[:len(clunk)]+=0.6*np.convolve(clunk,np.ones(8)/8,'same')
for st,f in [(0.06,2093),(0.16,2637)]:
    i=int(st*SR); tt=t[:n-i]
    b=sum(a*np.sin(2*np.pi*f*m*tt) for m,a in [(1,1),(2.01,.5),(3.02,.25),(4.1,.12)])*np.exp(-tt/.35)
    x[i:]+=b
save(OUT+'/kaching.wav',x)
# pop: soft bubbly pop for the match card (pleasant, short, no noise hiss)
n=int(SR*.35); t=np.arange(n)/SR
f=280+700*np.exp(-t/.025); ph=2*np.pi*np.cumsum(f)/SR
p=np.sin(ph)*np.exp(-t/.06)
p+=0.35*np.sin(2*np.pi*1760*t)*np.exp(-t/.09)+0.2*np.sin(2*np.pi*2640*t)*np.exp(-t/.07)
save(OUT+'/pop.wav',p)
# impact: deep cinematic boom + bright shimmer for the hook (frame 0)
n=int(SR*1.6); t=np.arange(n)/SR
f=40+90*np.exp(-t/.05); boom=np.sin(2*np.pi*np.cumsum(f)/SR)*np.exp(-t/.45)
hit=rng.standard_normal(n)*np.exp(-t/.03); hit=np.convolve(hit,np.ones(30)/30,'same')
sh=sum(np.sin(2*np.pi*fq*t+k)*np.exp(-t/.5) for k,fq in enumerate([1318,1975,2637,3951]))*0.12*np.minimum(t/.01,1)
save(OUT+'/impact.wav',boom+0.5*hit+sh)
# sparkle: lucky chime for the logo end card
n=int(SR*1.2); t=np.arange(n)/SR; x=np.zeros(n)
for k,(st,fq) in enumerate([(0,1568),(.07,2093),(.14,2637),(.21,3136)]):
    i=int(st*SR); tt=t[:n-i]; x[i:]+=np.sin(2*np.pi*fq*tt)*np.exp(-tt/.3)*(0.9-0.12*k)
save(OUT+'/sparkle.wav',x)
# phonk hit: distorted 808 drop + punchy kick + clap + down-sweep (cool, no bells) for the hook
n=int(SR*1.3); t=np.arange(n)/SR
f=48+70*np.exp(-t/.04); b808=np.tanh(2.8*np.sin(2*np.pi*np.cumsum(f)/SR))*np.exp(-t/.55)
kick=np.sin(2*np.pi*np.cumsum(55+260*np.exp(-t/.012))/SR)*np.exp(-t/.09)
cl=rng.standard_normal(n); cl=np.diff(np.concatenate([[0],cl]))*np.exp(-t/.05)*(t<.25)
sw=rng.standard_normal(n); out=np.zeros(n); lo=0.0
for i in range(n):
    a=0.02+0.25*np.exp(-t[i]/.25); lo+=a*(sw[i]-lo); out[i]=lo
sweep=out*np.exp(-t/.35)
save(OUT+'/phonk.wav',0.9*b808+0.7*kick+0.35*cl+0.6*sweep)
# coin: soft warm coin/chime for the slip (replaces the harsh ka-ching)
n=int(SR*.9); t=np.arange(n)/SR; x=np.zeros(n)
for st,fq in [(0,1175),(.08,1568)]:
    i=int(st*SR); tt=t[:n-i]; x[i:]+=(np.sin(2*np.pi*fq*tt)+.25*np.sin(2*np.pi*2*fq*tt))*np.exp(-tt/.22)*np.minimum(tt/.004,1)
k=int(SR*.03); x[:k]+=0.3*np.sin(2*np.pi*180*t[:k])*np.exp(-t[:k]/.01)
save(OUT+'/coin.wav',x)
```