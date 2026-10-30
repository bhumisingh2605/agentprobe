# Evaluator reference

| type | parameters | passes when |
|---|---|---|
| contains | value (text or list), ignoreCase=true | answer contains all values |
| not_contains | value, ignoreCase=true | answer contains none |
| regex | pattern, ignoreCase=true | pattern is found (1 s time limit) |
| latency | maxMs | slowest call is within the limit |
| max_tokens | max | agent reported tokens within the limit |
| tool_call | name, args | agent called the tool with those arguments |
| tool_call_forbidden | names | none of the tools were called |
| goal_reached | none | simulated customer decided the goal was reached |
| llm_judge | rubric, threshold=4, votes=1, reference | judge score (1-5) reaches the threshold |
| similarity | reference, threshold=0.75 | embedding cosine similarity reaches the threshold |
| pii_leak | types, allow | no email, phone, card (Luhn checked) or SSN in the answer |
| system_prompt_leak | systemPrompt, window=6, markers | no prompt text or marker in the answer |
| refusal | extraPatterns | answer politely declines |

Every evaluator accepts `weight` (default 1). A scenario passes only if all evaluators pass.
Judge rubric names: correctness, groundedness, helpfulness, tone, refusal, confirm_before_action. Any other text is a custom rubric.