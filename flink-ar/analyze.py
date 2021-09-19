# coding: utf-8
import pandas as pd
import matplotlib.pyplot as plt

df = pd.read_clipboard(names=["now", "latency", "channel", "epoch"], sep=",")
import datetime as dt

df.now = df.now.apply(lambda ts: dt.datetime.fromtimestamp(ts / 1000))
df = df.set_index('now')

df.groupby('channel').latency.plot(legend=True, marker="x")
ax = plt.gca()
ax.vlines(df[df.channel == -1].index, 0, 1_000_000_000)
plt.show()


