use auriya::cli;
use auriya::Result;

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<()> {
    cli::run().await
}
