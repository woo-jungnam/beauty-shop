import sys
from pathlib import Path

agent_dir = Path(__file__).resolve().parent / "product-agent"
sys.path.insert(0, str(agent_dir))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
    )
