package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.MoveProgress;
import com.mobybank.harness.domain.MoveRequest;
import com.mobybank.harness.domain.MoveStage;
import com.mobybank.harness.domain.SandboxFailureException;
import com.mobybank.harness.domain.SandboxTransfer;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * A stand-in transfer that reports the two stages the prototype shows ("Packaging context", then the transfer) and
 * waits between them, in either direction.
 * It can be told to refuse one direction, so a failed move can be seen without a real service.
 */
public final class FakeSandboxTransfer implements SandboxTransfer {

    private final Duration packaging;
    private final Duration transferring;
    private final FakeMoveFailure failure;

    public FakeSandboxTransfer(Duration packaging, Duration transferring) {
        this(packaging, transferring, FakeMoveFailure.NONE);
    }

    /** @param failure a direction to refuse after the packaging stage, as a real {@code sbx move} can fail */
    public FakeSandboxTransfer(Duration packaging, Duration transferring, FakeMoveFailure failure) {
        this.packaging = packaging;
        this.transferring = transferring;
        this.failure = failure;
    }

    @Override
    public void move(MoveRequest request, Consumer<MoveProgress> onProgress) {
        String summary = count(request.history().size(), "message") + " · " + count(request.files().size(), "file");

        onProgress.accept(new MoveProgress(MoveStage.PACKAGING, "Packaging context · " + summary));
        Pauses.sleep(packaging);

        if (failure.refuses(request.to())) {
            throw new SandboxFailureException("The fake sandbox service refused the move to "
                    + (request.to() == Location.CLOUD ? "the cloud" : "local") + " (harness.fake.move-failure)");
        }

        String action = request.to() == Location.CLOUD
                ? "Transferring files over private link"
                : "Restoring the session on this machine";
        onProgress.accept(new MoveProgress(MoveStage.TRANSFERRING, action + " · " + summary));
        Pauses.sleep(transferring);
    }

    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }
}
