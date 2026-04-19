use auriya::Result;
use auriya::cli;

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<()> {
    cli::run().await
}
