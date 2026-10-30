const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");

const PRIMARY_PORT = 3000;
const ENV_PORT = Number(process.env.PORT) || 0;
const HOST = "0.0.0.0";

const WEB_DIR = __dirname;
const PUBLIC_DIR = path.resolve(__dirname, "../public");
const DRAWABLE_DIR = path.resolve(__dirname, "../app/src/main/res/drawable");
const INDEX_HTML_PATH = path.join(WEB_DIR, "index.html");

const ASSET_MAP = {
  "/assets/aistudio/68e8db727540618044fd7e59_shutterstock_2300393367_cf09c248.jpg": {
    file: path.join(PUBLIC_DIR, "assets/aistudio/68e8db727540618044fd7e59_shutterstock_2300393367_cf09c248.jpg"),
    type: "image/jpeg",
  },
  "/assets/logo.png": {
    file: path.join(DRAWABLE_DIR, "img_launcher_bangkok.png"),
    type: "image/png",
  },
  "/assets/hero.jpg": {
    file: path.join(DRAWABLE_DIR, "img_main_hero_banner.jpg"),
    type: "image/jpeg",
  },
  "/assets/promo1.jpg": {
    file: path.join(DRAWABLE_DIR, "img_promo_banner_1.jpg"),
    type: "image/jpeg",
  },
  "/assets/promo2.jpg": {
    file: path.join(DRAWABLE_DIR, "img_promo_banner_2.jpg"),
    type: "image/jpeg",
  },
  "/assets/promo3.jpg": {
    file: path.join(DRAWABLE_DIR, "img_promo_banner_3.jpg"),
    type: "image/jpeg",
  },
};

const ASSET_LINKS_JSON = JSON.stringify(
  [
    {
      relation: ["delegate_permission/common.handle_all_urls"],
      target: {
        namespace: "android_app",
        package_name: "com.aistudio.winnerbalance.wbkpxq",
        sha256_cert_fingerprints: [
          "0D:6E:E7:03:48:44:C6:4A:A6:8A:CD:5A:80:AC:63:94:A5:6E:6E:00:71:BC:C2:7F:74:58:EC:52:40:0B:06:5D",
        ],
      },
    },
  ],
  null,
  2
);

const APK_CANDIDATES = [
  path.resolve(__dirname, "../.build-outputs/app-debug.apk"),
  path.resolve(__dirname, "../app/build/outputs/apk/debug/app-debug.apk"),
];

function resolveLatestApkPath() {
  let newestFile = null;
  let newestMtime = -1;
  for (const candidate of APK_CANDIDATES) {
    try {
      const stat = fs.statSync(candidate);
      if (stat.isFile() && stat.size > 0 && stat.mtimeMs > newestMtime) {
        newestMtime = stat.mtimeMs;
        newestFile = { file: candidate, size: stat.size };
      }
    } catch (_) {
      // Ignore missing build candidate
    }
  }
  return newestFile;
}

function requestHandler(req, res) {
  const parsedUrl = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  const pathname = parsedUrl.pathname;

  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("X-Content-Type-Options", "nosniff");

  if (pathname === "/healthz") {
    res.writeHead(200, { "Content-Type": "application/json; charset=utf-8" });
    res.end(JSON.stringify({ ok: true, service: "winner-balance-public-portal" }));
    return;
  }

  if (pathname === "/download/Winner-Balance.apk" || pathname === "/download/apk") {
    const apkInfo = resolveLatestApkPath();
    if (!apkInfo) {
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("APK build is currently being prepared. Please try again in a moment.");
      return;
    }
    res.writeHead(200, {
      "Content-Type": "application/vnd.android.package-archive",
      "Content-Disposition": 'attachment; filename="Winner-Balance.apk"',
      "Content-Length": String(apkInfo.size),
      "Cache-Control": "no-store",
    });
    if (req.method === "HEAD") {
      res.end();
      return;
    }
    fs.createReadStream(apkInfo.file).pipe(res);
    return;
  }

  if (pathname === "/.well-known/assetlinks.json") {
    res.writeHead(200, {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "public, max-age=300",
    });
    res.end(ASSET_LINKS_JSON);
    return;
  }

  if (pathname === "/favicon.ico") {
    const logoEntry = ASSET_MAP["/assets/logo.png"];
    if (logoEntry && fs.existsSync(logoEntry.file)) {
      res.writeHead(200, { "Content-Type": "image/png", "Cache-Control": "public, max-age=86400" });
      fs.createReadStream(logoEntry.file).pipe(res);
      return;
    }
    res.writeHead(204);
    res.end();
    return;
  }

  if (Object.prototype.hasOwnProperty.call(ASSET_MAP, pathname)) {
    const asset = ASSET_MAP[pathname];
    if (fs.existsSync(asset.file)) {
      res.writeHead(200, {
        "Content-Type": asset.type,
        "Cache-Control": "public, max-age=3600",
      });
      fs.createReadStream(asset.file).pipe(res);
      return;
    }
  }

  // Refresh-safe SPA & query-route handler:
  // Serves index.html with HTTP 200 OK for `/`, `/?u=...`, `/u/...`, and any browser refresh route (zero 404s).
  fs.readFile(INDEX_HTML_PATH, "utf8", (err, html) => {
    if (err) {
      res.writeHead(500, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("Winner Balance web portal unavailable.");
      return;
    }
    res.writeHead(200, {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-cache",
    });
    res.end(html);
  });
}

// 1. Bind port 3000 for AI Studio's reverse proxy (8080 -> 3000)
const primaryServer = http.createServer(requestHandler);
primaryServer.on("error", (err) => {
  if (err.code === "EADDRINUSE") {
    console.log(`Port ${PRIMARY_PORT} is already serving Winner Balance.`);
  } else {
    console.error("Primary server error:", err);
  }
});
primaryServer.listen(PRIMARY_PORT, HOST, () => {
  console.log(`Winner Balance Public Web Portal listening on http://${HOST}:${PRIMARY_PORT}`);
});

// 2. If deployed in a standalone Cloud Run container where PORT (e.g. 8080) is not held by a reverse proxy, bind it too
if (ENV_PORT > 0 && ENV_PORT !== PRIMARY_PORT) {
  const secondaryServer = http.createServer(requestHandler);
  secondaryServer.on("error", (err) => {
    if (err.code === "EADDRINUSE") {
      // Expected inside AI Studio workspace where the container gateway proxy owns port 8080 and forwards to 3000
      return;
    }
    console.error("Secondary port listener error:", err);
  });
  secondaryServer.listen(ENV_PORT, HOST, () => {
    console.log(`Winner Balance Public Web Portal also listening on http://${HOST}:${ENV_PORT}`);
  });
}
