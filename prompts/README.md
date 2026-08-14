# prompts/

应用自身调用 LLM 用的**提示词模板**：system prompt、few-shot 示例、工具说明、评审 rubric 等。

**可以放**：版本化、可 diff 的纯文本（`.md` / `.txt` / `.jinja`），按用途或代理分文件——把提示词从代码里抽出来，便于迭代与评审。
> 这是**应用运行时**的提示词；给开发助手 Claude 的指令是 `CLAUDE.md`，私有模型转储在 `_local/models/`，三者勿混。
