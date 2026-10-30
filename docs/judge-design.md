# Judge design notes

- Rubrics have anchored scores (what 1, 3 and 5 look like). The model writes reasoning and evidence first, then the score.
- Temperature 0. Optional majority vote (median of 1 to 5 calls).
- The customer message, tool calls and agent answer are untrusted data: wrapped in tags, tag lookalikes stripped,
  and the system prompt says never to follow instructions inside them.
- Replies are parsed leniently (markdown fences, score as string) but validated strictly (score 1 to 5, JSON object);
  invalid replies are retried with backoff, then reported as "Judge unavailable".
- Every result stores the judge model and prompt version, so scores from different judges are never mixed silently.
- Calibration: blind human labels, then percent agreement and Cohen's kappa (Landis and Koch bands).