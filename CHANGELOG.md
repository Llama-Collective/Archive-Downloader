## 1.0.18-1.0.19
- Added semantic search. It uses a local machine learning model to find the most relevant posts for a given query, even when it's not an exact match. Everything is done locally, so no data is sent to any server, but it does have ask you for permission before downloading the model and relevant data, which in total is around 60MB.
- Fixed UI conflict with syncmatica revolution mod.
- 1.0.19: Fixed a bug where the mod would keep model loaded in memory even when not needed.