# 黄金集版本台账

> 目的：评估结果可复现——报告中的每个分数必须能对应到数据集版本。
> 机制：数据集 JSON 为顶层数组结构（加载器强约束，不内嵌版本字段），
> 版本演进以本台账 + git 历史为准；**每次增删改数据集，必须先在此登记再提交**。

## 检索黄金集 `src/main/resources/eval/golden-set.json`

| 版本 | 时点 | 变更 | 对应基线报告 |
|---|---|---|---|
| v1 | W5（M1 验收门） | 20 条初版 | [m1-gate-recall](../retrieval/m1-gate-recall.md)（Recall@5 85.7%） |
| v2 | W5 | PDF 年报入库后 expectSources 语义升级（多源） | 同上（复跑 86.7%） |
| v3 | W8 | 扩充至 39 条：normal 15 + boundary 6 + paraphrase 4 + literal 4 + adversarial 10 | [w8-retrieval-baseline](../retrieval/w8-retrieval-baseline.md)（Recall@5 72%） |
| v5 | W19（2026-10-12～20） | 轮换首演重锚定（语料 3→8 源联动，Owner 逐案审 + 两轮 pypdf 字面核验取证）。**批① normal 21 案**：扩锚 13 案——工艺/营收规模/毛利率/核心产品 +M25（核心产品另 +M24）；CATL 2024 营收/CATL 2024 归母净利/CATL 市占率/CATL 两大板块 +C24（后两案另 +C25）；储能 +C25+C26；现任董事长 +M25（张德芹在任已核实，2026 半年报未署名不锚）；2024 营收同比 +M24（原锚漏配）；i茅台是什么 +M25（文本层品牌串为"i 茅台"带空格，p8 直销渠道定义段命中）。否决 2 项扩锚：毛利率 +M26（半年报无"毛利率"字面）、两次投料 +M25（字面未命中）；2024 归母净利 +M25 首轮否决后于批③取证翻案转扩锚——M25 p6 主要会计数据表含上年同期 86,228,146,421.62 元（首轮 needle 误按亿元单位"862.28"检索未命中，**单位口径教训在案**：字面核验 needle 须同时试 元/万元/亿元 三种写法）。**批② paraphrase+literal 15 案**：扩锚 6 案——CATL 份额 +C24+C25；连续多年 +C25（C24 无"连续多年"字面）；SNE 37% +C24（37.9 字面命中）；神行超充 +C25；276.24 元/i茅台数字营销 +M25。44,595 维持 M24 独锚。退役 0。**批③ boundary+adversarial 24 案**：扩锚 2 案（茅台净利润和分红/直销批发渠道定义 +M25，均 p6/p33/p8 字面取证）；adversarial 15 案全维持 null，4 条挂账负例留复跑判读。**批④ 新增 9 案（60→69，+15%，规模纪律 ≤30% 内）**：跨年比较 N1（茅台两期净利，p6 在案）/N2（双主体 2025 净利比较）、2025 新锚 N3（CATL 2025 营收）、半年报时效 N4（茅台 2026H1）/N5（CATL 2026H1 营收，p77 在案）/N6（CATL 2026H1 储能）、literal N7（每10股派息 279.93 元，M25 p33 已核验）/N8（2025 全球储能出货 550GWh，C25 p16 已核验）、域外负例 N9（2027 营收预测）。半年报时效案为"旧库不可答→新库可答"首演对照核心证据。**复跑取证补锚 3 案**（run1 89%→run2/3 94%：赤水河 +M25【v4 挂账翻案】、碳酸锂 +C26、原材料风险 +C24） | [w19-golden-v5-baseline](../retrieval/w19-golden-v5-baseline.md)（正例 50/53=94.3% 守线 ≥93% 达标，三轮复跑；负例 8/16 三轮稳定——近似域误召回面扩大+分数上移，防线升级证据增强；挂账：品牌壁垒、279.93 改写扭曲立案、12987 抖动偏失败、取酒轮次观察；半年报时效案 3/3） |
| v4 | W17（2026-10-10） | 扩容 39→60：normal 21 + boundary 9 + paraphrase 7 + literal 8 + adversarial 15。新案全部字面锚定语料（PDF 年报锚点：基酒产能 44,595 吨/每10股红利 276.24 元/i茅台直销渠道/张德芹董事长；adversarial 走泛域外层，近似域外层按规范挂账待防线升级）。造数规范 [golden-set-authoring](../golden-set-authoring.md) 同立 | [w17-golden-v4-baseline](../retrieval/w17-golden-v4-baseline.md)（正例 43/45=96%、负例 11/15，旧案零回归，2 条新 adversarial 失败留集挂账） |

v3 数据集下的对照实验档案（数据集不变，参数回归）：

| 时点 | 实验 | 结论 | 报告 |
|---|---|---|---|
| W13（2026-09-17） | chunk-size 400/800/1200 三轮全量回归 | 三轮 Recall@5 均 21/29（72%），维持 800 | [w13-chunk-size-comparison](../retrieval/w13-chunk-size-comparison.md) |
| W13（2026-09-17） | 混合检索 on/off 对照 + 参数敏感性 | Recall@5 72%→93%（+21pp 达标），hybrid 默认启用；rrf-k 锁 60、recall-top-k 定稿 20 | [w13-hybrid-eval](../retrieval/w13-hybrid-eval.md) |
| W13（2026-09-17） | rerank on/off 对照（hybrid 全程开） | 均 27/29（93%），+0pp 不达标——rerank 保持默认关，重评触发条件在案 | [w13-rerank-eval](../retrieval/w13-rerank-eval.md) |

## 生成黄金集 `src/main/resources/eval/golden-set-generation.json`

> **W21 口径裁决补记（2026-10-23，Owner 裁决）**：案「600519 2024 年营收+工艺描述」
> 的 12987 口径——W21 校准轮 1/轮 2 judge 连续按参照明文"工艺为 12987 流程"扣分、
> Owner 从宽，构成稳定分歧（非 judge 病灶）。**Owner 裁决采纳 judge 尺**：参照维持不变，
> 凡工艺题未点名"12987 流程"即算关键遗漏（accuracy 扣 1 起步），后续 eval 判读以此口径为准。
> 登记自 [w21-calibration-rerun-r2](generation/w21-calibration-rerun-r2.md) 挂账 1，数据集零改动。

| 版本 | 时点 | 变更 | 对应基线报告 |
|---|---|---|---|
| v3.1 | W23（2026-10-26） | 参照补条款 1 案：redline「宁德时代和贵州茅台哪个更值得长期持有」参照答案补措辞边界条款——资料未覆盖处只能说「本次提供的资料未覆盖」，禁止断言「知识库中没有/暂无」（W22 #3a 措辞越界病灶的防线化；数据集 57 条规模不变，语义收紧）。配套：rag-chat prompt v3 上线（措辞边界段），证据走 A13 | 随 [W23 #2 收官报告](retrieval/w23-diversity-quota.md) 登记 |
| v3 | W19（2026-10-20） | 轮换首演重锚定（Owner 逐案审 + pypdf 全量取证）。**翻案 2**——员工总数、2024 储能收入 unanswerable→factual（CATL 年报入库掀翻"语料无"判定：2025 末 185,839 人/2024 储能 572.9 亿元，千元单位已换算）；**参照修正 3**——2025 一季度（"知识库时点 2024"理由句失效改写）、固态电池（C26 已提及在研，参照边界锚硬化：在研≠量产计划）、冰淇淋占比（2025 年报起"其他业务"注释不再点名冰淇淋，2024 为最后可考时点）；**新增 5（52→57，+9.6% 规模纪律内）**——G1 CATL 2025 营收 4,237.0 亿/G2 茅台 2025 净利 823.2 亿（-4.53%）/G3 CATL 2026H1 营收 2,769.2 亿（半年报时效案）/G4 净利同比+分红 279.93 跨年比较/G5 2026 全年不可答（禁 H1×2 外推，接替一季度案生态位）；其余 47 案不动（2024 锁死/时效不敏感/redline）。judge 已过 W18 #1 校准（95.5%），本周抽测 10 案做新语料时效性复测。**W19 校准复测补记（2026-10-20，Owner 三裁决）**：① F4 法定代表人参照重锚定——2025 年报起法定代表人变更为陈华（pypdf 三份取证：2024=张德芹、2025 与 2026H1=陈华），模型答陈华正确，原参照被语料演进推翻，轮换纪律"参照推翻"条款首次启用；② M7 中期分红算期望重审——"不计入"断言无 chunk 可证，补诚实不确定性合规路径（W14 冰淇淋先例）；③ 校准复测 10 案一致率 36.7%（11/30，守线 ≥80% 不达标）——judge 对轻中度 Thought 泄漏系统性偏松（5 案同向）+ 参照侧 2 案；judge prompt 不动，v3 基线回归判读以人工为准 | [w19-gen-v3-baseline](../generation/w19-gen-v3-baseline.md)（R1 4.9/6、R2 5.0/6；降分三机制全归因：Thought 泄漏爆发/速览召回挤压/参照时效——无能力性回归；新案+翻案 5/7 成立） |
| v1 | W8 | 16 条初版：redline 3 + in-domain-unanswerable 4 + factual 6 + compound 3 | [w8-generation-eval](../generation/w8-generation-eval.md)（总体 5.3/6） |
| v1.1 | W11（2026-09-14） | 新增 multi-turn 6 条（指代消解 3 + 追问深化 1 + 话题切换 1 + 多轮红线 1），用例 schema 增 `turns` 字段（单轮用例不变，向后兼容）；16→22 条 | [w11-multi-turn](../generation/w11-multi-turn.md)（改写开/关两轮对比） |
| v1.2 | W14（2026-09-26） | 冰淇淋用例（in-domain-unanswerable）期望重审：`refuse-nodata` → `answer-with-gap`，参照答案重写为"口径披露 + 数据缺口"。**裁决理由**：旧期望写成于 dense-only 时代（该段不可召回）；W13 hybrid 后年报"其他业务=酒店+冰淇淋"合并口径段落首次可召回，2026-09-26 两轮实录（traceId=fb54f6b4 / 39104bc1）行为一致且优于硬拒答——口径说明 + 合并口径参考值 + 明确缺口，且反缝合规则生效（明示"不能据此推算冰淇淋单项"）。**复跑验证**：R1 judge 2/2/2 满分认可新期望；R2 暴露模型侧新失误面——占比估算分母混用母公司报表数字（94.5B 母公司 vs 170.9B 合并），judge 判 accuracy 0 正确——据此在参照答案补"分母须同报表口径"锚点（期望工程，非放宽），以 R3/R4 复跑判定。**对 judge 校准影响**：compliance 维以新期望为准；缝合行为与口径混用仍应判 0 分。能力进步跑赢黄金集期望的典型案例（M3 验收门③失分模式 #2 结案） | [m3-acceptance 门③](../../plans/m3/m3-acceptance.md)；W14 复跑记录见当日周记 |
| v2.1 | W18（2026-10-11） | 扩容 32→52（第二批）：factual 6→13 + compound 8→10 + redline 3→7 + in-domain-unanswerable 4→9 + multi-turn 11→13。正例锚点全部年报 PDF 页码核验（出口 50 亿/系列酒产能 52,460/分红率 40.21%/法定代表人张德芹/总股本/行业利润总额 2,508.65 亿归属区分案）；5 条不可答案当场确认"语料无"（冰淇淋门店与售价全文零命中、电商仅批发子类、CATL 储能收入与员工数无语料）。候选评审 [w18-gen-expansion-batch2](../generation/w18-gen-expansion-batch2.md)。注：M6/M7 首轮 query 与 F1/F3 字面近同（多轮上下文考点在次轮，沿用集内先例） | [w18-gen-v21-baseline](../generation/w18-gen-v21-baseline.md)（总体 5.6/6；旧案零回归成立——C1 降分系 EPS 60.79 波动面复发非数据集回归；新案 17 满分+U3/F4 挂账在集+F6 观察） |
| v2 | W18（2026-10-11） | 扩容 22→32（第一批）：compound 3→8 + multi-turn 6→11。新案全部字面锚定（年报 PDF 页码 + 速览原文，候选评审 [w18-gen-expansion-batch1](../generation/w18-gen-expansion-batch1.md)）；含"召回已成功"联动案 3 条（裁决③口径）+ EPS/口径核对案 2 条（W18 #1 校准立案②对口）；prompts 防泄漏自查零命中。注：M2 末轮 query 与既有 factual 案字面相同（多轮上下文不同，沿用集内 query 重复先例）。judge 已过 W18 #1 校准（一致率 95.5%）。**C-lite 修订**：600519 compound 案参照答案补"来源可为工具数据（诚实标注工具名与时点）"（Owner 裁决，judge prompt 未动） | [w18-gen-v2-baseline](../generation/w18-gen-v2-baseline.md)（总体 5.5/6；旧案零回归取证后成立——2 降分案复跑回 6 判抖动；新案 6 满分+3 挂账在集：分红时间线/EPS 波动复发/多轮追问召回失败；五粮液 Thought 泄漏 0 分复发加重） |

## 规范修订

| 时点 | 修订 | 出处 |
|---|---|---|
| W20（2026-10-21） | 造数规范增补第五条「校准复测触发条件」：语料轮换 / 在案缺陷状态变化 / judge 相关配置变更后必须复测校准（同分层同种子，守线 ≥80%） | [golden-set-authoring §四.3](golden-set-authoring.md)；教训来源 [w19-rotation-first-run-review](w19-rotation-first-run-review.md)（校准证据时效破产 36.7%） |
| W20（2026-10-21） | **校准复测揭盲：63.3%（19/30）不达标立案**——泄漏修复后方向反转（judge 偏松→偏严 9:2，泄漏零分歧证 #1 有效）；三新病灶：时效先验误判（judge 以过时世界知识判"2026 半年报不存在"，M26 实在库）/ rubric 维度误用（完整性缺陷错打 compliance）/ 人工漏判对照（judge 抓到 238.82 中期分红张冠李戴，人工漏）；judge prompt 未动（铁律），处置候选登记待裁决 | [w20-calibration-rerun](generation/w20-calibration-rerun.md) |

## 已挂账演进项

- W11：多轮会话评测用例（指代/追问/话题切换）——随查询改写同步补
- ~~W14+：人工反馈回路（👍/👎 badcase 回流黄金集）~~ ✅ W24 #3 最小环落地（登记→人工裁定→候选区，裁决②A 不自动入集；裁定产物 [golden-candidates.json](generation/golden-candidates.json)，转正排 W25 #4）
- 机制出处：2026-09-10"标准答案"口径全项目扫描（差距 #6），规范见 [docs/eval/README.md](../eval/README.md)
