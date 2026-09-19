Quantitative Risk Engine: Multi-Asset Portfolio VaR & Expected Shortfall Analytics

A quantitative risk measurement and backtesting framework built in R for multi-asset portfolios.

Key Features & Implementations
- Financial Data Pipeline: Built an automated ETL pipeline using R (`quantmod`, `openxlsx`) to extract multi-year financial time series from Yahoo Finance and feed clean market data into risk models.
- Rolling-Window Backtesting: Implemented an iterative rolling-window engine (n = 250 daily samples over a 7-year time horizon) to dynamically compute daily risk metrics across 1,500+ trading days.
- Multi-Model Risk Quantification:** Developed modular risk measurement methods to calculate Value at Risk (VaR) and Expected Shortfall (ES) using Historical Simulation, Parametric Normal, and Monte Carlo Simulation (100,000 iterations under joint normal distribution assumptions).

 Tech Stack
- Language: R, Java
- Packages: `quantmod`, `openxlsx`
- Environment: RStudio, Eclipse
