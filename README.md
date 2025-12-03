# TCP Benchmark: Conexão Direta e Proxy (Otimizado / Não Otimizado)

Este projeto permite avaliar o desempenho de conexões TCP em diferentes cenários de rede, utilizando clientes e servidores diretos, e um proxy com ou sem otimizações.  
Logs detalhados são gerados em CSV e podem ser visualizados através de gráficos.

---

## Estrutura do Projeto

| Tipo | Classes / Arquivos | Observações |
|------|-----------------|------------|
| **Direto** | `ServidorDireto.java` / `ClienteDireto.java` | Conexão TCP direta, sem proxy |
| **Proxy Otimizado** | `ClientMain.java` | Proxy com otimizações ativadas (`enableOptimization = true`) |
| **Proxy Não Otimizado** | `ClientMain.java` + `ProxyPipe` | Proxy com `enableOptimization = false` (desabilita otimizações) |

---

## Pré-requisitos

- Docker (para rodar containers isolados)  
- Java 17+  
- Python 3 + pandas + matplotlib (para gerar gráficos)  
- `tc` (Traffic Control) disponível no container / host Linux  

---

## Compilação dos Clientes / Servidores

Basta compilar os arquivos `.java` manualmente:

```bash
# Compilar servidor direto
javac network/ServidorDireto.java

# Compilar cliente direto
javac network/ClienteDireto.java

# Compilar cliente para proxy
javac network/ClientMain.java

Cenários de Rede com `tc`
-------------------------

Cada cenário altera atraso, perda e largura de banda da interface `eth0`:

### Cenário 1: RTT baixo, 0% perda

`tc qdisc del dev eth0 root || true
java network.ClientMain    # ou ClienteDireto`

### Cenário 2: 50ms de atraso, 1% de perda

`tc qdisc del dev eth0 root || true
tc qdisc add dev eth0 root netem delay 50ms loss 1%
java network.ClientMain
tc qdisc del dev eth0 root`

### Cenário 3: 100ms de atraso, 2% de perda

`tc qdisc del dev eth0 root || true
tc qdisc add dev eth0 root netem delay 100ms loss 2%
java network.ClientMain
tc qdisc del dev eth0 root`

### Cenário 4: Limitação de banda 5 Mbps

`tc qdisc del dev eth0 root || true
tc qdisc add dev eth0 root tbf rate 5mbit burst 32kbit latency 400ms
java network.ClientMain
tc qdisc del dev eth0 root`

### Cenário 5: Combinação atraso + perda + limitação

`tc qdisc del dev eth0 root || true
tc qdisc add dev eth0 root handle 1: netem delay 100ms loss 2%
tc qdisc add dev eth0 parent 1: handle 10: tbf rate 5mbit burst 32kbit latency 400ms
java network.ClientMain
tc qdisc del dev eth0 root`

* * * * *

Tipos de Conexão e Execução
---------------------------

### 1\. TCP Direto

-   Classes: `ServidorDireto.java` + `ClienteDireto.java`

-   Execução:

`# Rodar servidor
java network.ServidorDireto

# Rodar cliente
java network.ClienteDireto`

### 2\. Proxy Não Otimizado

-   Classe: `ClientMain.java`

-   Alteração: em `ProxyPipe.java`, defina

`private final boolean enableOptimization = false;`

-   Executar:

`docker compose down
docker compose build
docker compose up -d
java network.ClientMain`

### 3\. Proxy Otimizado

-   Classe: `ClientMain.java`

-   Alteração: `enableOptimization = true` (padrão)

-   Executar:

`docker compose down
docker compose up -d
java network.ClientMain`

* * * * *

Logs e CSV
----------

-   Logs são gerados em `logs/`

-   Exemplo de CSV: `logs/client_direct.csv`

-   Colunas:

`epoch_ms,c2s_bytes,s2c_bytes,rtt_us,rttvar_us,throughput_Bps,status,algorithm,buffer_size,cwnd,ssthresh`

> Dica: limpe ou renomeie arquivos antes de cada novo teste para evitar acúmulo de métricas.

* * * * *

Gerar Gráficos
--------------

1.  Instalar dependências Python:

`pip install pandas matplotlib`

1.  Executar script:

`python gerar_graficos.py logs/client_direct.csv`

-   O script gera 4 gráficos:

    1.  RTT

    2.  Throughput

    3.  CWND (janela de congestionamento)

    4.  Buffer TCP + marcação de mudanças de algoritmo

-   O gráfico é salvo como `logs/client_direct.png` e exibido na tela.

* * * * *

Ordem sugerida de execução
--------------------------

1.  Limpar `tc` antes de cada cenário:

`tc qdisc del dev eth0 root || true`

1.  Executar **TCP Direto** → gerar CSV

2.  Executar **Proxy Não Otimizado** → gerar CSV

3.  Executar **Proxy Otimizado** → gerar CSV

4.  Gerar gráficos para cada CSV

5.  Comparar métricas entre conexões e cenários
