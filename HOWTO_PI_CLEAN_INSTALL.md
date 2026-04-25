# Grimmory OPF Branch: Clean Install And Upstream Sync

คู่มือนี้อธิบาย 2 เรื่อง:

1. ติดตั้ง Grimmory ใหม่บน Raspberry Pi แบบ clean
2. sync โค้ดตาม Grimmory ต้นทางในอนาคต

คู่มือนี้ใช้ branch:

- Fork: `https://github.com/captainboto/grimmory.git`
- Branch: `codex/opf-support`

จุดสำคัญของ branch นี้:

- เพิ่มการอ่านไฟล์ OPF ตอน scan library โดยตรง
- ใช้ได้กับ setup ที่เป็น `DISK_TYPE=NETWORK`
- ไม่ต้องใช้ sidecar import เพื่อดึง OPF metadata

---

## 1. Clean Install บน Raspberry Pi

### 1.1 สิ่งที่ต้องมี

- Raspberry Pi OS 64-bit หรือ Linux ที่รัน Docker ได้
- Docker
- Docker Compose
- พื้นที่เก็บข้อมูลสำหรับ:
  - app data
  - database
  - หนังสือ

ตรวจสอบก่อน:

```bash
docker --version
docker compose version
git --version
```

### 1.2 clone repo

```bash
cd ~
git clone -b codex/opf-support https://github.com/captainboto/grimmory.git
cd grimmory
```

ตรวจสอบว่าอยู่ branch ถูกต้อง:

```bash
git branch --show-current
```

ผลควรเป็น:

```text
codex/opf-support
```

### 1.3 สร้างโฟลเดอร์สำหรับ runtime

```bash
mkdir -p data
mkdir -p bookdrop
mkdir -p mariadb/config
```

### 1.4 สร้างไฟล์ `docker-compose.yml`

สร้างไฟล์นี้ในโฟลเดอร์ `~/grimmory/docker-compose.yml`

```yaml
services:
  grimmory:
    build: .
    container_name: grimmory
    environment:
      - USER_ID=1000
      - GROUP_ID=1000
      - TZ=Asia/Bangkok
      - DATABASE_URL=jdbc:mariadb://mariadb:3306/grimmory
      - DATABASE_USERNAME=grimmory
      - DATABASE_PASSWORD=change_me
      - API_DOCS_ENABLED=false
      - DISK_TYPE=NETWORK
    depends_on:
      mariadb:
        condition: service_healthy
    ports:
      - "6060:6060"
    volumes:
      - ./data:/app/data
      - /media/QNAP_Books/Books:/books
      - ./bookdrop:/bookdrop
    restart: unless-stopped

  mariadb:
    image: lscr.io/linuxserver/mariadb:11.4.8
    container_name: mariadb
    environment:
      - PUID=1000
      - PGID=1000
      - TZ=Asia/Bangkok
      - MYSQL_ROOT_PASSWORD=change_me_root
      - MYSQL_DATABASE=grimmory
      - MYSQL_USER=grimmory
      - MYSQL_PASSWORD=change_me
    volumes:
      - ./mariadb/config:/config
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "mariadb-admin", "ping", "-h", "localhost"]
      interval: 5s
      timeout: 5s
      retries: 10
```

### 1.5 ปรับค่าที่ต้องแก้ก่อนรัน

ต้องแก้อย่างน้อย:

- `DATABASE_PASSWORD=change_me`
- `MYSQL_ROOT_PASSWORD=change_me_root`
- `MYSQL_PASSWORD=change_me`

ถ้า path หนังสือไม่ใช่ `/media/QNAP_Books/Books` ให้แก้บรรทัดนี้:

```yaml
- /media/QNAP_Books/Books:/books
```

### 1.6 validate compose

```bash
docker compose config
```

ถ้าไม่มี error แปลว่า YAML ใช้ได้

### 1.7 build และรัน

```bash
docker compose build
docker compose up -d
```

### 1.8 ดู log

```bash
docker compose logs -f grimmory
```

### 1.9 เปิดใช้งาน

เปิดผ่าน browser:

```text
http://<IP-ของ-Pi>:6060
```

แล้วสร้าง admin account ตามขั้นตอนปกติ

### 1.10 เพิ่ม library

ใน Grimmory ให้เพิ่ม library path เป็น:

```text
/books
```

และแนะนำให้ตั้ง:

- `Metadata source`: จะตั้งเป็นอะไรก็ได้ตาม flow ของคุณ
- `DISK_TYPE=NETWORK`: ใช้ต่อได้

เพราะ branch นี้อ่าน OPF ตอน scan ตรงจากไฟล์ข้างหนังสือแล้ว

---

## 2. Clean Rebuild เมื่อต้องการอัปเดต branch นี้

ถ้าคุณใช้ branch นี้อยู่แล้วบน Pi:

```bash
cd ~/grimmory
git fetch origin
git switch codex/opf-support
git pull
docker compose build
docker compose up -d
```

ถ้าต้องการดู log หลังอัปเดต:

```bash
docker compose logs -f grimmory
```

---

## 3. Sync ตาม Grimmory ต้นทาง ทำอย่างไร

มี 2 ระดับ

### แบบ A: บน Pi เอาแค่ของที่ push มาแล้วจาก fork

อันนี้ง่ายสุด และเหมาะกับเครื่อง production:

```bash
cd ~/grimmory
git fetch origin
git switch codex/opf-support
git pull
docker compose build
docker compose up -d
```

หมายความว่า:

- upstream Grimmory จะถูก merge จากเครื่อง dev ก่อน
- แล้วค่อย push มาที่ fork
- Pi ดึงจาก fork อย่างเดียว

นี่คือวิธีที่แนะนำที่สุดสำหรับเครื่องใช้งานจริง

### แบบ B: sync upstream โดยตรงใน repo ที่ใช้พัฒนา

อันนี้ใช้บนเครื่อง dev ของคุณ ไม่แนะนำให้ทำตรงบน Pi ถ้า Pi เป็นเครื่อง production

ตรวจสอบ remote:

```bash
git remote -v
```

รูปแบบที่ควรเป็น:

- `origin` = fork ของคุณ
- `upstream` = repo หลักของ Grimmory

sync แบบแนะนำ:

```bash
git fetch upstream
git switch develop
git merge --ff-only upstream/develop
git switch codex/opf-support
git merge develop
git push
```

ความหมาย:

1. ดึงของใหม่จาก Grimmory หลัก
2. อัปเดต local `develop`
3. merge ของใหม่เข้า branch `codex/opf-support`
4. push branch ใหม่ขึ้น fork
5. จากนั้นค่อยไป pull ที่ Pi

---

## 4. Recommended Workflow

แนะนำ flow แบบนี้:

### บนเครื่อง dev

```bash
git fetch upstream
git switch develop
git merge --ff-only upstream/develop
git switch codex/opf-support
git merge develop
git push
```

### บน Pi

```bash
cd ~/grimmory
git fetch origin
git switch codex/opf-support
git pull
docker compose build
docker compose up -d
```

แบบนี้ปลอดภัยกว่า เพราะ:

- Pi ไม่ต้องรับ merge conflict เอง
- Pi รับแต่โค้ดที่ merge และ push แล้ว
- maintenance ง่ายกว่ามาก

---

## 5. ถ้าติดปัญหาบ่อย มีจุดไหนให้เช็กก่อน

### `fatal: not a git repository`

แปลว่าอยู่ผิดโฟลเดอร์ หรือยังไม่ได้ `git clone`

### `docker compose up -d --build` แล้วบอกว่าไม่มี compose file

แปลว่ายังไม่มี `docker-compose.yml` ในโฟลเดอร์นั้น

### `yaml: line 1: did not find expected key`

แปลว่าไฟล์ YAML พัง ต้องเช็ก indentation หรือสร้างใหม่

### แอปขึ้น แต่ metadata OPF ไม่เข้า

เช็ก:

- mount หนังสือถูก path หรือไม่
- library ใน Grimmory ชี้ไป `/books` หรือไม่
- มีไฟล์ `.opf` อยู่ข้างไฟล์หนังสือจริงหรือไม่
- มีการ rescan library หลัง deploy แล้วหรือไม่

---

## 6. คำสั่งสรุปแบบสั้นมาก

### ติดตั้งใหม่

```bash
cd ~
git clone -b codex/opf-support https://github.com/captainboto/grimmory.git
cd grimmory
docker compose config
docker compose build
docker compose up -d
```

### อัปเดตบน Pi

```bash
cd ~/grimmory
git fetch origin
git switch codex/opf-support
git pull
docker compose build
docker compose up -d
```

### อัปเดตตาม upstream บนเครื่อง dev

```bash
git fetch upstream
git switch develop
git merge --ff-only upstream/develop
git switch codex/opf-support
git merge develop
git push
```
