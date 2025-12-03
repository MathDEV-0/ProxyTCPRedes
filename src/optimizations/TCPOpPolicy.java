package optimizations;

import metrics.TCPMetrics;

public interface TCPOpPolicy {
    void apply(byte[] buf, int len);

    /** Novo método para retornar o tamanho do buffer atual */
    default int getSendBufferSize() {
        return 8192; // valor default se não implementado
    }

    static TCPOpPolicy defaultPolicies(TCPMetrics m) {
        return new TCPOpPolicy() {

            private final DelayPolicy pacing = new DelayPolicy(m);
            private final DelayedAckPolicy delayed = new DelayedAckPolicy(m);
            private final AlgorithmSelector selector = new AlgorithmSelector(m);

            @Override
            public void apply(byte[] buf, int len) {

                // primeiro atualiza heurística
                selector.apply(buf, len);

                // escolhe política conforme heurística
                switch (selector.getCurrentPolicy()) {

                    case SAFE:
                        delayed.apply(buf, len); // min interferência
                        break;

                    case CONSERVATIVE:
                        delayed.apply(buf, len);
                        pacing.apply(buf, len); // leve pacing
                        break;

                    case BALANCED:
                        pacing.apply(buf, len);
                        delayed.apply(buf, len);
                        break;

                    case AGGRESSIVE:
                        pacing.apply(buf, len); // mais pacing, sem delay ACK
                        break;
                }
            }

            @Override
            public int getSendBufferSize() {
                // delega para o AlgorithmSelector
                return selector.getSendBufferSize();
            }
        };
    }
}
