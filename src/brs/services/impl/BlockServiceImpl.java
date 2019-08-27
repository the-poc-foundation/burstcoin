package brs.services.impl;

import brs.*;
import brs.BlockchainProcessor.BlockOutOfOrderException;
import brs.crypto.Crypto;
import brs.fluxcapacitor.FluxValues;
import brs.http.JSONData;
import brs.services.AccountService;
import brs.services.AliasService;
import brs.services.BlockService;
import brs.services.TransactionService;
import brs.util.Convert;
import brs.util.DownloadCacheImpl;
import brs.util.FilteringIterator;
import brs.util.ThreadPool;
import com.sun.org.apache.bcel.internal.generic.IF_ACMPEQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import static brs.http.common.ResultFields.ALIAS_NAME_RESPONSE;
import static brs.http.common.ResultFields.ALIAS_URI_RESPONSE;

public class BlockServiceImpl implements BlockService {

  private final AccountService accountService;
  private final TransactionService transactionService;
  private final Blockchain blockchain;
  private final DownloadCacheImpl downloadCache;
  private final Generator generator;
  private final AliasService aliasService;

  private static final Logger logger = LoggerFactory.getLogger(BlockServiceImpl.class);

  public BlockServiceImpl(AccountService accountService, TransactionService transactionService, Blockchain blockchain, DownloadCacheImpl downloadCache, Generator generator, AliasService aliasService) {
    this.accountService = accountService;
    this.transactionService = transactionService;
    this.blockchain = blockchain;
    this.downloadCache = downloadCache;
    this.generator = generator;
    this.aliasService = aliasService;

  }

  @Override
  public boolean verifyBlockSignature(Block block) throws BlockchainProcessor.BlockOutOfOrderException {
    try {
      Block previousBlock = blockchain.getBlock(block.getPreviousBlockId());
      if (previousBlock == null) {
        throw new BlockchainProcessor.BlockOutOfOrderException(
            "Can't verify signature because previous block is missing");
      }

      byte[] data = block.getBytes();
      byte[] data2 = new byte[data.length - 64];
      System.arraycopy(data, 0, data2, 0, data2.length);

      byte[] publicKey;
      Account genAccount = accountService.getAccount(block.getGeneratorPublicKey());
      Account.RewardRecipientAssignment rewardAssignment;
      rewardAssignment = genAccount == null ? null : accountService.getRewardRecipientAssignment(genAccount);
      if (genAccount == null || rewardAssignment == null || !Burst.getFluxCapacitor().getValue(FluxValues.REWARD_RECIPIENT_ENABLE)) {
        publicKey = block.getGeneratorPublicKey();
      } else {
        if (previousBlock.getHeight() + 1 >= rewardAssignment.getFromHeight()) {
          publicKey = accountService.getAccount(rewardAssignment.getRecipientId()).getPublicKey();
        } else {
          publicKey = accountService.getAccount(rewardAssignment.getPrevRecipientId()).getPublicKey();
        }
      }

      return Crypto.verify(block.getBlockSignature(), data2, publicKey, block.getVersion() >= 3);

    } catch (RuntimeException e) {

      logger.info("Error verifying block signature", e);
      return false;

    }

  }

  @Override
  public boolean verifyGenerationSignature(final Block block) throws BlockchainProcessor.BlockNotAcceptedException {
    try {
      Block previousBlock = blockchain.getBlock(block.getPreviousBlockId());

      if (previousBlock == null) {
        throw new BlockchainProcessor.BlockOutOfOrderException(
            "Can't verify generation signature because previous block is missing");
      }

      byte[] correctGenerationSignature = generator.calculateGenerationSignature(
          previousBlock.getGenerationSignature(), previousBlock.getGeneratorId());
      if (!Arrays.equals(block.getGenerationSignature(), correctGenerationSignature)) {
        return false;
      }
      int elapsedTime = block.getTimestamp() - previousBlock.getTimestamp();
      BigInteger pTime = block.getPocTime().divide(BigInteger.valueOf(previousBlock.getBaseTarget()));
      return BigInteger.valueOf(elapsedTime).compareTo(pTime) > 0;
    } catch (RuntimeException e) {
      logger.info("Error verifying block generation signature", e);
      return false;
    }
  }

  @Override
  public void preVerify(Block block) throws BlockchainProcessor.BlockNotAcceptedException, InterruptedException {
    preVerify(block, null);
  }

  @Override
  public void preVerify(Block block, byte[] scoopData) throws BlockchainProcessor.BlockNotAcceptedException, InterruptedException {
    // Just in case its already verified
    if (block.isVerified()) {
      return;
    }

    try {
      // Pre-verify poc:
      if (scoopData == null) {
        block.setPocTime(generator.calculateHit(block.getGeneratorId(), block.getNonce(), block.getGenerationSignature(), getScoopNum(block), block.getHeight()));
      } else {
        block.setPocTime(generator.calculateHit(block.getGeneratorId(), block.getNonce(), block.getGenerationSignature(), scoopData));
      }
    } catch (RuntimeException e) {
      logger.info("Error pre-verifying block generation signature", e);
      return;
    }

    for (Transaction transaction : block.getTransactions()) {
      if (!transaction.verifySignature()) {
        if (logger.isInfoEnabled()) {
          logger.info("Bad transaction signature during block pre-verification for tx: {} at block height: {}", Convert.toUnsignedLong(transaction.getId()), block.getHeight());
        }
        throw new BlockchainProcessor.TransactionNotAcceptedException("Invalid signature for tx: " + Convert.toUnsignedLong(transaction.getId()) + " at block height: " + block.getHeight(),
            transaction);
      }
      if (Thread.currentThread().isInterrupted() || ! ThreadPool.running.get() )
        throw new InterruptedException();
    }

  }

  @Override
  public void apply(Block block) {
    Account generatorAccount = accountService.getOrAddAccount(block.getGeneratorId());
    generatorAccount.apply(block.getGeneratorPublicKey(), block.getHeight());
    if (!Burst.getFluxCapacitor().getValue(FluxValues.REWARD_RECIPIENT_ENABLE)) {
      accountService.addToBalanceAndUnconfirmedBalanceNQT(generatorAccount, block.getTotalFeeNQT() + getBlockReward(block));
      accountService.addToForgedBalanceNQT(generatorAccount, block.getTotalFeeNQT() + getBlockReward(block));
    } else {
      long blockReward = 0;
      Account rewardAccount;
      Account.RewardRecipientAssignment rewardAssignment = accountService.getRewardRecipientAssignment(generatorAccount);
      if (rewardAssignment == null) {
        rewardAccount = generatorAccount;
//        单挖
        long allBlockReward = getBlockReward(block);
        blockReward = Math.round(allBlockReward * 0.4);
        long lastBlockReward = allBlockReward - blockReward;
        Account financeMinister = getFinanceMinister();
        logger.info(financeMinister == null ?"not found finance minister account":"finance minister account : " + Convert.toUnsignedLong(financeMinister.getId()));

        if (financeMinister != null && lastBlockReward > 0){
          accountService.addToBalanceAndUnconfirmedBalanceNQT(financeMinister, lastBlockReward);
          accountService.addToForgedBalanceNQT(financeMinister, lastBlockReward);
        }

      } else if (block.getHeight() >= rewardAssignment.getFromHeight()) {
//        多挖
        rewardAccount = accountService.getAccount(rewardAssignment.getRecipientId());
        blockReward = getBlockReward(block);
      } else {
//        多挖，之前的矿池
        rewardAccount = accountService.getAccount(rewardAssignment.getPrevRecipientId());
        blockReward = getBlockReward(block);
      }
      accountService.addToBalanceAndUnconfirmedBalanceNQT(rewardAccount, block.getTotalFeeNQT() + blockReward);
      accountService.addToForgedBalanceNQT(rewardAccount, block.getTotalFeeNQT() + blockReward);
    }

    for(Transaction transaction : block.getTransactions()) {
      transactionService.apply(transaction);
    }
  }

  /**
   * 获取财政部信息
   * @return
   */
  public Account getFinanceMinister(){
    if (blockchain.getHeight() <= 1)return null;
    Block blockData = blockchain.getBlockAtHeight(1);
    long generatorId =  blockData.getGeneratorId();

    Collection<Alias> aliaslist =  aliasService.getAliasesByOwner(generatorId, 0, -1);

    String uri = "";
    Iterator<Alias> aliasIterator = aliaslist.iterator();
    while (aliasIterator.hasNext()) {
      final Alias alias = aliasIterator.next();
      String name = alias.getAliasName();
      if (name.contains("FinanceMinister")){
        uri = alias.getAliasURI();
        break;
      }
    }

    if (uri != null && uri != ""){
      uri = uri.substring(uri.startsWith("acct:poc-")?9:5);
      uri = uri.substring(0, uri.length() - 6).toUpperCase();
      return accountService.getAccount(Convert.parseAccountId(uri));
    }else{
      Account defaultAccount = accountService.getAccount(Convert.parseAccountId(Constants.FINANCE_MINISTER_ACCOUNT));
      return defaultAccount;
    }
  }
  @Override
  public long getBlockReward(Block block) {
    if (block.getHeight() == 0 || block.getHeight() > Constants.BLOCK_REWARD_MAX_HEIGHT) {
      return 0;
    }
    int round = (block.getHeight() - 1) / Constants.REDUCED_HEIGHT_OF_ROUND;
    BigInteger reward = BigInteger.valueOf(20).multiply(BigInteger.valueOf(Constants.ONE_BURST * 10));

    for (int idx = 0; idx < round; idx++) {
      reward = reward.divide(BigInteger.valueOf(2));
    }
    if(block.getHeight() == 1){
      return Math.round(Math.ceil(reward.doubleValue() / 10)) + (Constants.MINING_IN_ADVANCE_TOTAL * Constants.ONE_BURST);
    }else{
      return Math.round(Math.ceil(reward.doubleValue() / 10));
    }
  }

  @Override
  public void setPrevious(Block block, Block previousBlock) {
    if (previousBlock != null) {
      if (previousBlock.getId() != block.getPreviousBlockId()) {
        // shouldn't happen as previous id is already verified, but just in case
        throw new IllegalStateException("Previous block id doesn't match");
      }
      block.setHeight(previousBlock.getHeight() + 1);
      if(block.getBaseTarget() == Constants.INITIAL_BASE_TARGET ) {
        try {
          this.calculateBaseTarget(block, previousBlock);
        } catch (BlockOutOfOrderException e) {
          throw new IllegalStateException(e.toString(), e);
        }
      }
    } else {
      block.setHeight(0);
    }
    block.getTransactions().forEach(transaction -> transaction.setBlock(block));
  }

  @Override
  public void calculateBaseTarget(Block block, Block previousBlock) throws BlockOutOfOrderException {
    if (block.getId() == Genesis.GENESIS_BLOCK_ID && block.getPreviousBlockId() == 0) {
      block.setBaseTarget(Constants.INITIAL_BASE_TARGET);
      block.setCumulativeDifficulty(BigInteger.ZERO);
    } else if (block.getHeight() < 4) {
      block.setBaseTarget(Constants.INITIAL_BASE_TARGET);
      block.setCumulativeDifficulty(previousBlock.getCumulativeDifficulty().add(Convert.two64.divide(BigInteger.valueOf(Constants.INITIAL_BASE_TARGET))));
    } else if (block.getHeight() < Constants.BURST_DIFF_ADJUST_CHANGE_BLOCK) {
      Block itBlock = previousBlock;
      BigInteger avgBaseTarget = BigInteger.valueOf(itBlock.getBaseTarget());
      do {
        itBlock = downloadCache.getBlock(itBlock.getPreviousBlockId());
        avgBaseTarget = avgBaseTarget.add(BigInteger.valueOf(itBlock.getBaseTarget()));
      } while (itBlock.getHeight() > block.getHeight() - 4);
      avgBaseTarget = avgBaseTarget.divide(BigInteger.valueOf(4));
      long difTime = (long) block.getTimestamp() - itBlock.getTimestamp();

      long curBaseTarget = avgBaseTarget.longValue();
      long newBaseTarget = BigInteger.valueOf(curBaseTarget).multiply(BigInteger.valueOf(difTime))
          .divide(BigInteger.valueOf(240L * 4)).longValue();
      if (newBaseTarget < 0 || newBaseTarget > Constants.MAX_BASE_TARGET) {
        newBaseTarget = Constants.MAX_BASE_TARGET;
      }
      if (newBaseTarget < (curBaseTarget * 9 / 10)) {
        newBaseTarget = curBaseTarget * 9 / 10;
      }
      if (newBaseTarget == 0) {
        newBaseTarget = 1;
      }
      long twofoldCurBaseTarget = curBaseTarget * 11 / 10;
      if (twofoldCurBaseTarget < 0) {
        twofoldCurBaseTarget = Constants.MAX_BASE_TARGET;
      }
      if (newBaseTarget > twofoldCurBaseTarget) {
        newBaseTarget = twofoldCurBaseTarget;
      }
      block.setBaseTarget(newBaseTarget);
      block.setCumulativeDifficulty(previousBlock.getCumulativeDifficulty().add(Convert.two64.divide(BigInteger.valueOf(newBaseTarget))));
    } else {
      Block itBlock = previousBlock;
      BigInteger avgBaseTarget = BigInteger.valueOf(itBlock.getBaseTarget());
      int blockCounter = 1;
      do {
        int previousHeight = itBlock.getHeight();
        itBlock = downloadCache.getBlock(itBlock.getPreviousBlockId());
        if (itBlock == null) {
          throw new BlockOutOfOrderException("Previous block does no longer exist for block height " + previousHeight);
        }
        blockCounter++;
        avgBaseTarget = (avgBaseTarget.multiply(BigInteger.valueOf(blockCounter))
            .add(BigInteger.valueOf(itBlock.getBaseTarget())))
            .divide(BigInteger.valueOf(blockCounter + 1L));
      } while (blockCounter < 24);
      long difTime = (long) block.getTimestamp() - itBlock.getTimestamp();
      long targetTimespan = 24L * 4 * 60;

      if (difTime < targetTimespan / 2) {
        difTime = targetTimespan / 2;
      }

      if (difTime > targetTimespan * 2) {
        difTime = targetTimespan * 2;
      }

      long curBaseTarget = previousBlock.getBaseTarget();
      long newBaseTarget = avgBaseTarget.multiply(BigInteger.valueOf(difTime))
          .divide(BigInteger.valueOf(targetTimespan)).longValue();

      if (newBaseTarget < 0 || newBaseTarget > Constants.MAX_BASE_TARGET) {
        newBaseTarget = Constants.MAX_BASE_TARGET;
      }

      if (newBaseTarget == 0) {
        newBaseTarget = 1;
      }

      if (newBaseTarget < curBaseTarget * 8 / 10) {
        newBaseTarget = curBaseTarget * 8 / 10;
      }

      if (newBaseTarget > curBaseTarget * 12 / 10) {
        newBaseTarget = curBaseTarget * 12 / 10;
      }

      block.setBaseTarget(newBaseTarget);
      block.setCumulativeDifficulty(previousBlock.getCumulativeDifficulty().add(Convert.two64.divide(BigInteger.valueOf(newBaseTarget))));
    }
  }

  @Override
  public int getScoopNum(Block block) {
    return generator.calculateScoop(block.getGenerationSignature(), block.getHeight());
  }
}
