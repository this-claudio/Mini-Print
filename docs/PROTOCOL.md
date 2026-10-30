# Protocolo das mini impressoras térmicas

Notas de interoperabilidade sobre as mini impressoras térmicas Bluetooth de 58 mm
(384 pontos/linha), reunidas a partir de testes com o hardware e de documentação
pública da comunidade.

## Família pelo nome Bluetooth

O nome anunciado (minúsculo) costuma indicar o protocolo:

| Protocolo | Nomes | Largura |
|---|---|---|
| Cat printer | `gt01 gb01-04 xw003/5/7/9 jx001/6 m01 m02 pr02 pr07 ly01-03 ly10 x5-x8` | 384 |
| Cat printer | `yt01 yt02 mx01-mx11 miniprinter bq0x mxw lp100 …` | 384 |
| Cat printer | `xw004/6/8 jx002`, `lp100s ly100 lp220 p100 mp100 …` | 576 @ 304 dpi |
| ESC/POS GS v 0 | `mr.in* mht-* yhk* g5-*` | 384 |
| ESC/POS genérico | resto (576 se nome contém `80`) | 384 |

A impressora do projeto (YHK-ECFA) usa **ESC/POS GS v 0**, 384 pontos/linha, dual-mode (BR/EDR + LE).

## Transporte BLE

- `connectGatt(autoConnect=false, TRANSPORT_LE)`, prioridade HIGH, `discoverServices`.
- Característica de escrita (ordem de preferência): `49535343-8841-43F4-A8D4-ECBE34729BB3`, `2AF1`, `AE01`, `FF02`, `FFE1`, `AB01`, `AE03`, `AE10`, `FFA1`; senão a primeira com WRITE/WRITE_NO_RESPONSE. Write type = NO_RESPONSE.
- Notify: `49535343-1E4D-4BD9-BA61-23C647249616`, `2AF0`, `AE02`, `FF01`, `FFE2`, `AB02`, `203A`, `204A`.
- MTU: ESC/POS = `largura/8 + 11` (59), cat printer = 248. Pacote = MTU − 3.

## ESC/POS GS v 0

```
1B 40                          ESC @ (init, opcional)
1D 76 30 00 xL xH yL yH dados  GS v 0: x = bytes por linha (48), y = linhas
1D 72 31                       GS r 1: pede status (resposta = 1 byte) — usado p/ cadenciar
1E 47 03                       info do aparelho: "HV=…,SV=…,VOLT=…mv,DPI=…,"
```
- Bit 1 = ponto preto, MSB = ponto mais à esquerda.
- Em BLE, mandar **1 linha por comando** e esperar a resposta do status (ou um timeout) evita estourar o buffer.
- Avanço de papel: imagem branca de `n × 32` linhas.

## ESC/POS variante `1F` — não implementado

```
1B 40 ; 1F 27 01 <bytesPorLinha> 88     init
1F 2A wL wH <linha>                     linha inteira
1F 2B <byteInicial> <qtdBytes> <dados>  só o trecho não-branco
1F 2E n                                 repete a última linha n+1 vezes
1F 2A 01 00 00                          linha branca
1B 4A n                                 avança n pontos
```

## Cat printer — não implementado

Pacote: `51 78 CMD 00 LEN_lo LEN_hi DADOS CRC8 FF` (CRC-8 poly 0x07 só sobre DADOS).

| CMD | Nome | Dados |
|---|---|---|
| `A3` | GET_STATUS | `01` — resposta `51 78 A3 01 … status` (bit0 sem papel, bit1 tampa, bit2 quente, bit7 ocupado) |
| `A4` | SET_HEAT_DENSITY | `01` leve / `03` médio / `05` forte |
| `AF` | ENERGY | u16 LE: 8600 / 12000 / 16000 |
| `BD` | PAPER_FEED_SPEED | `2D` |
| `BE` | DRAFT/modo | `00` antes de imprimir |
| `A2` | PRINT_DATA | 48 bytes; ordem dos bits (MSB ou LSB primeiro) varia — conferir no hardware |
| `BF` | PRINT_DATA_COMPRESS | RLE: byte = `(cor << 7) \| comprimento(1..127)` |

Antes de cada bloco de 64 linhas, consultar status e esperar sair de BUSY/"too hot".
