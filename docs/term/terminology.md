# AI 工程术语词汇卡

> 整理日期:2026-10-10 · 按学习主题分组
> 音标为通用美式发音,加粗音节为重读
> **归属**:ai-evolution 项目文档(docs/term/),随代码版本管理
> **维护惯例**:每次对话聊到新概念时,由 Kimi 主动追加词条到对应分组;更新均在此文件进行

---

## 一、RAG 与检索链路

| 术语 | 音标 | 中文 | 一句话 |
|---|---|---|---|
| RAG (Retrieval-Augmented Generation) | /ræɡ/ | 检索增强生成 | 先检索资料再让模型回答 |
| chunk / chunking | /tʃʌŋk/ / -ɪŋ/ | 分块 | 把文档切成语义小块 |
| embedding | /ɪmˈbedɪŋ/ | 嵌入(向量) | 把文本压成语义向量 |
| dense retrieval | /dens/ | 稠密检索 | 语义匹配,向量相似度 |
| sparse retrieval | /spɑːrs/ | 稀疏检索 | 关键词匹配,BM25 流派 |
| BM25 (Best Matching 25) | 读字母 B-M-twenty-five | 最佳匹配25号 | 关键词检索 30 年基线 |
| TF (Term Frequency) | 读字母 | 词频 | 词在这篇文档里出现多不多(要饱和压平) |
| IDF (Inverse Document Frequency) | 读字母 | 逆文档频率 | 词在整个语料里稀不稀有;越稀有越值钱 |
| hybrid search | /ˈhaɪbrɪd/ | 混合检索 | dense+sparse 两路并行 |
| RRF (Reciprocal Rank Fusion) | /rɪˈsɪprəkəl ræŋk ˈfjuːʒn/ | 倒数名次融合 | 比名次不比分数的融合算法 |
| reranker | /ˌriːˈræŋkər/ | 重排序器 | 对候选逐对精排打分 |
| bi-encoder | /baɪ ɪnˈkoʊdər/ | 双编码器 | 问题和文档分别编码,快 |
| cross-encoder | /krɔːs ɪnˈkoʊdər/ | 交叉编码器 | 拼接一起编码,准 |
| ColBERT | /koʊlˈbɛrt/ | (专名) | token 级迟交互检索 |
| HyDE (Hypothetical Document Embeddings) | /haɪd/ | 假设性文档嵌入 | 先编个假答案再拿它检索 |
| query rewrite | /ˈkwɪri/ | 查询改写 | 把问题问得更清楚 |
| multi-query / RAG Fusion | — | 多问检索融合 | 一个问题换多种问法分别查 |

## 二、模型架构

| 术语 | 音标 | 中文 | 一句话 |
|---|---|---|---|
| Transformer | /trænsˈfɔːrmər/ | (架构名) | 现代 LLM 的统一架构 |
| attention | /əˈtenʃn/ | 注意力机制 | 每个词检索全句,决定关注谁 |
| self-attention | — | 自注意力 | 句子内部互相看 |
| multi-head attention | — | 多头注意力 | 多个"检索频道"并行 |
| encoder | /ɪnˈkoʊdər/ | 编码器 | 双向看全句,输出向量(BERT系) |
| decoder | /diːˈkoʊdər/ | 解码器 | 单向看左边,逐词生成(GPT系) |
| LLM (Large Language Model) | 读字母 | 大语言模型 | 生成型选手 |
| token | /ˈtoʊkən/ | 词元 | 模型处理的最小文本单位 |
| softmax | /ˈsɒftmæks/ | (函数名) | 把分数归一化成概率分布 |
| cosine similarity | /ˈkoʊsaɪn/ | 余弦相似度 | 向量方向有多像 |
| parameter | /pəˈræmɪtər/ | 参数 | 模型学到的权重 |
| pretrain / fine-tune | /ˌpriːˈtreɪn/ /faɪn tjuːn/ | 预训练 / 微调 | 打底子 / 针对性补课 |

## 三、推理与 Agent

| 术语 | 音标 | 中文 | 一句话 |
|---|---|---|---|
| CoT (Chain-of-Thought) | 读字母 | 思维链 | 让模型写出推理过程 |
| ToT (Tree-of-Thoughts) | 读字母 | 思维树 | 可搜索、可回溯的多路推理 |
| Self-Consistency | /ˌself kənˈsɪstənsi/ | 自洽性 | 多想几遍,多数投票 |
| ReAct (Reasoning + Acting) | /riˈækt/ | 推理+行动 | 想—做—看 的 Agent 循环 |
| Thought / Action / Observation | — | 思考/行动/观察 | ReAct 循环三件套 |
| Agent | /ˈeɪdʒənt/ | 智能体 | 能自主用工具的模型系统 |
| MCP (Model Context Protocol) | 读字母 | 模型上下文协议 | 工具层的 USB-C 接口 |
| A2A (Agent2Agent) | 读字母 | 智能体间协议 | Agent 互相协作的协议 |
| hallucination | /həˌluːsɪˈneɪʃn/ | 幻觉 | 模型自信地胡说 |
| prompt | /prɑːmpt/ | 提示词 | 给模型的输入指令 |
| router / routing | /ˈruːtər/ | 路由 | 按难度/意图分发到不同模型 |
| cascade | /kæˈskeɪd/ | 级联 | 小模型先答,不行再升级 |
| temperature | /ˈtemprətʃər/ | 温度(采样参数) | 控制输出随机性:0 最确定,>1 更发散 |
| temperature drift | — | 温度漂移 | 同一 prompt 多次调用结果不一;源于采样随机、批处理浮点误差、模型静默升级 |
| top_p (nucleus sampling) | — | 核采样 | 只保留累计概率达 P 的头部候选词再采样 |
| top_k | — | Top-K 截断 | 只保留概率最高的 K 个候选词 |
| presence / frequency penalty | /ˈprezns/ /ˈfriːkwənsi/ /ˈpenəlti/ | 存在/频率惩罚 | 抑制复读:出现过就扣分 / 出现越多扣越多 |
| stop sequence | — | 停止序列 | 模型生成到指定文本就停,Agent 截断 Action 靠它 |
| seed | /siːd/ | 随机种子 | 固定种子提高输出可复现性 |
| beam search | — | 束搜索 | 每步保留 B 条最优路径;不采样走搜索 |
| uvx | 读字母 U-V-X | (uv 的工具运行器) | 免安装临时运行 Python 工具 |
| KV cache | /keɪ viː kæʃ/ | 键值缓存 | 存下历史 token 的 K/V 免重算;显存换计算 |
| prefill / decode | /ˌpriːˈfɪl/ /diːˈkoʊd/ | 预填充 / 解码 | 推理两阶段:一口气吃掉 prompt / 逐词生成 |
| TTFT (Time To First Token) | 读字母 | 首 token 延迟 | 发请求到收到第一个字;感知"快不快" |
| TPOT (Time Per Output Token) | 读字母 | 每 token 间隔 | 流式输出的"打字速度" |
| throughput | /ˈθruːpʊt/ | 吞吐量 | 单位时间处理的 token 数;与延迟互相制约 |

## 四、评估与上线

| 术语 | 音标 | 中文 | 一句话 |
|---|---|---|---|
| golden dataset | — | 黄金集 | 带标准答案的评测集 |
| rubric | /ˈruːbrɪk/ | 评分细则 | 分维度打分的评价量规 |
| Recall@K | /rɪˈkɔːl/ | 召回率@K | 前 K 条里命中的比例 |
| MRR (Mean Reciprocal Rank) | 读字母 | 平均倒数排名 | 第一个正确答案排多前 |
| nDCG | 读字母 | 归一化折损累计增益 | 考虑分级的精细排序指标 |
| Hit Rate | — | 命中率 | 前 K 里至少中一条的比例 |
| LLM-as-a-Judge | — | LLM 当裁判 | 用强模型给输出打分 |
| blind / double-blind eval | — | 盲评 / 双盲评测 | 裁判不知道答案出自哪个模型,防品牌偏置 |
| position bias | — | 位置偏置 | 裁判偏爱先看到的答案;随机换位/双向评测破解 |
| verbosity bias | /vərˈbɑːsəti/ | 冗长偏置 | 裁判偏爱更长的答案;rubric 明说不以长度计分 |
| faithfulness | /ˈfeɪθflnəs/ | 忠实度 | 答案有没有瞎编 |
| A/B test | — | A/B 测试 | 随机分流,真实用户投票 |
| canary release | /kəˈneri/ | 金丝雀发布(灰度) | 小流量试水,渐进放量 |
| rollback | /ˈroʊlbæk/ | 回滚 | 出问题秒切回旧版 |
| pp (percentage point) | — | 百分点 | 百分比的差值(5%→7% 是 +2pp) |
| bp (basis point) | — | 基点 | 100bp = 1pp,财经常用 |

## 五、读音常见坑

| 易错词 | 正确读法 | 常见错误 |
|---|---|---|
| reciprocal | ri-**SIP**-rə-kəl | 重音不在第一音节 |
| cosine | **KOH**-sain | 不是 "ko-sin" |
| cache | 同 "cash" /kæʃ/ | 不是 "catch" |
| epoch | **EP**-ək | AI 里不读 "ee-pok" |
| niche | /niːʃ/ "尼什" | 不是 "nit-ch" |
| gauge | /ɡeɪdʒ/ "给只" | Micrometer 里的仪表,不是 "高只" |
