import pandas as pd
import matplotlib.pyplot as plt
import sys
import os
from datetime import datetime
import matplotlib.dates as mdates

def plot_log(csv_file):
    if not os.path.exists(csv_file):
        print(f"Erro: Arquivo '{csv_file}' não encontrado.")
        return

    df = pd.read_csv(csv_file)

    df['Timestamp'] = df['epoch_ms'].apply(lambda x: datetime.fromtimestamp(x / 1000.0))

    fig, (ax1, ax2, ax3, ax4) = plt.subplots(4, 1, figsize=(12, 16), sharex=True)
    plt.subplots_adjust(hspace=0.3)
    fig.suptitle(f'Análise de Conexão TCP\nArquivo: {csv_file}', fontsize=16)

    # --- GRÁFICO 1: RTT ---
    ax1.plot(df['Timestamp'], df['rtt_us'], color='tab:red')
    ax1.set_ylabel('RTT (us)')
    ax1.set_title('Evolução do RTT (Round Trip Time)')
    ax1.grid(True, linestyle='--', alpha=0.6)

    # --- GRÁFICO 2: THROUGHPUT ---
    ax2.plot(df['Timestamp'], df['throughput_Bps'], color='tab:blue')
    ax2.set_ylabel('Throughput (Bytes/s)')
    ax2.set_title('Taxa de Transferência')
    ax2.grid(True, linestyle='--', alpha=0.6)

    # --- GRÁFICO 3: CWND ---
    ax3.plot(df['Timestamp'], df['cwnd'], color='tab:green', label='CWND')
    ax3.set_ylabel('CWND (pacotes)')
    ax3.set_title('Janela de Congestionamento (CWND)')
    ax3.grid(True, linestyle='--', alpha=0.6)
    
    # Marca trocas de algoritmo no CWND
    if 'algorithm' in df.columns:
        mudancas = df[df['algorithm'] != df['algorithm'].shift(1)]
        for _, row in mudancas.iterrows():
            ax3.annotate(
                f"{row['algorithm']}",
                xy=(row['Timestamp'], row['cwnd']),
                xytext=(0, 15),
                textcoords='offset points',
                arrowprops=dict(facecolor='black', shrink=0.05),
                ha='center'
            )

    # --- GRÁFICO 4: BUFFER ---
    ax4.plot(df['Timestamp'], df['buffer_size'], color='tab:purple')
    ax4.set_ylabel('Buffer (Bytes)')
    ax4.set_title('Tamanho do Buffer TCP')
    ax4.grid(True, linestyle='--', alpha=0.6)

    # Marca trocas de algoritmo no buffer também
    if 'algorithm' in df.columns:
        for _, row in mudancas.iterrows():
            ax4.annotate(
                f"{row['algorithm']}",
                xy=(row['Timestamp'], row['buffer_size']),
                xytext=(0, 15),
                textcoords='offset points',
                arrowprops=dict(facecolor='black', shrink=0.05),
                ha='center'
            )

    ax4.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S'))
    plt.xticks(rotation=45)
    plt.xlabel('Tempo')

    png_name = csv_file.replace('.csv', '.png')
    plt.savefig(png_name, dpi=300, bbox_inches='tight')
    print(f"[OK] Gráfico salvo como: {png_name}")

    plt.show()

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: python gerar_graficos.py arquivo.csv")
    else:
        plot_log(sys.argv[1])
