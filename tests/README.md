# tests/

项目级测试根：集成 / 端到端 / 跨模块。模块级单元测试可就近放各包内（如 `backend/tests/`）。

约定：默认确定性 / 离线——LLM 调用走 mock 或录制回放、禁运行期出站，由 `conftest.py` autouse fixture + `scripts/verify.ps1` 强制（见 CLAUDE.md 硬边界）。
