package io.korion.offlinepay.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.korion.offlinepay.application.port.FoxCoinWalletSnapshotPort;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class FoxCoinWalletSnapshotAdapterTest {

    @Test
    void readsCanonicalWalletSnapshot() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://foxya.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://foxya.test/api/v1/internal/wallets/snapshot?userId=1&currencyCode=KORI"))
                .andExpect(header("x-internal-api-key", "test-key"))
                .andRespond(withSuccess("""
                        {
                          "userId": 1,
                          "currencyCode": "KORI",
                          "totalBalance": "197.343487",
                          "lockedBalance": "0.000000",
                          "canonicalBasis": "FOX_CLIENT_VISIBLE_AVAILABLE_KORI_EXCLUDING_OFFLINE_COLLATERAL",
                          "refreshedAt": "2026-06-27T00:00:00Z"
                        }
                        """, APPLICATION_JSON));

        FoxCoinWalletSnapshotPort.WalletSnapshot snapshot =
                new FoxCoinWalletSnapshotAdapter(builder.build(), "test-key")
                        .getCanonicalWalletSnapshot(1L, "KORI");

        assertEquals(new BigDecimal("197.343487"), snapshot.totalBalance());
        assertEquals("FOX_CLIENT_VISIBLE_AVAILABLE_KORI_EXCLUDING_OFFLINE_COLLATERAL", snapshot.canonicalBasis());
        server.verify();
    }

    @Test
    void rejectsIncompleteWalletSnapshotInsteadOfTreatingItAsZero() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://foxya.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://foxya.test/api/v1/internal/wallets/snapshot?userId=1&currencyCode=KORI"))
                .andRespond(withSuccess("""
                        {
                          "userId": 1,
                          "currencyCode": "KORI",
                          "lockedBalance": "0.000000",
                          "canonicalBasis": "FOX_CLIENT_VISIBLE_AVAILABLE_KORI_EXCLUDING_OFFLINE_COLLATERAL"
                        }
                        """, APPLICATION_JSON));

        FoxCoinWalletSnapshotAdapter adapter = new FoxCoinWalletSnapshotAdapter(builder.build(), "test-key");

        assertThrows(IllegalStateException.class, () -> adapter.getCanonicalWalletSnapshot(1L, "KORI"));
        server.verify();
    }
}
