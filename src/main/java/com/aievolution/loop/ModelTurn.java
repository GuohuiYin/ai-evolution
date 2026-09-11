package com.aievolution.loop;

/** 模型在 Loop 中的一步决策（W11 #1）。sealed 层次——新增决策类型必须显式 permits， 编译器守住穷举（同 ChatStreamPart 先例）。 */
public sealed interface ModelTurn permits ModelTurn.Act, ModelTurn.Final {

  /** 继续研究：思考后请求调用一个工具 */
  record Act(String thought, String tool, String input) implements ModelTurn {}

  /** 收敛：思考后给出最终回答，循环退出 */
  record Final(String thought, String answer) implements ModelTurn {}
}
